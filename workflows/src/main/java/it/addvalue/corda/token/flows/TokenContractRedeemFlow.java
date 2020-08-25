package it.addvalue.corda.token.flows;

import co.paralleluniverse.fibers.Suspendable;
import it.addvalue.corda.token.contracts.TokenContract;
import it.addvalue.corda.token.states.TokenState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about
 * the IOU encapsulated within an [TokenState].
 *
 * <p>In our simple example, the [Acceptor] always accepts a valid TokenState.
 *
 * <p>These flows have deliberately been implemented by using only the call() method for ease of
 * understanding. In practice we would recommend splitting up the various stages of the flow into
 * sub-routines.
 *
 * <p>All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable
 * annotation.
 */
public class TokenContractRedeemFlow {
  @InitiatingFlow
  @StartableByRPC
  public static class Initiator extends FlowLogic<SignedTransaction> {

    @NotNull private final List<TokenState> inputTokens;

    private final Step GENERATING_TRANSACTION =
        new Step("Generating transaction based on new IOU.");
    private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
    private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
    private final Step GATHERING_SIGS =
        new Step("Gathering the counterparty's signature.") {
          @Override
          public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.Companion.tracker();
          }
        };
    private final Step FINALISING_TRANSACTION =
        new Step("Obtaining notary signature and recording transaction.") {
          @Override
          public ProgressTracker childProgressTracker() {
            return FinalityFlow.Companion.tracker();
          }
        };

    // The progress tracker checkpoints each stage of the flow and outputs the specified messages
    // when each
    // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within
    // the call()
    // function.
    private final ProgressTracker progressTracker =
        new ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION);

    public Initiator(@NotNull final List<TokenState> inputTokens) {

      if (inputTokens.stream().anyMatch(tokenState -> tokenState.getAmount() <= 0)) {
        throw new IllegalArgumentException(("Amount must be positive"));
      }

      //noinspection ConstantConditions
      if (inputTokens == null) {
        throw new IllegalArgumentException(("InputTokens cannot be null"));
      }

      //noinspection ConstantConditions
      if (inputTokens.isEmpty()) {
        throw new IllegalArgumentException("InputTokens cannot be empty");
      }
      this.inputTokens = inputTokens;
    }

    @Override
    public ProgressTracker getProgressTracker() {
      return progressTracker;
    }

    private Optional<StateAndRef<TokenState>> findTokenState(TokenState tokenState) {
      QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
      List<StateAndRef<TokenState>> tokenStates =
          getServiceHub().getVaultService().queryBy(TokenState.class, criteria).getStates();

      // fitro i token uguali a quelli di input
      return tokenStates.stream()
          .filter(
              tokenStateStateAndRef ->
                  tokenStateStateAndRef.getState().getData().equals(tokenState))
          .findFirst();
    }

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

      // Obtain a reference to a notary we wish to use.
      /**
       * METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and
       * single-notary networks only!* METHOD 2: Explicit selection of notary by CordaX500Name -
       * argument can by coded in flow or parsed from config (Preferred)
       *
       * <p>* - For production you always want to use Method 2 as it guarantees the expected notary
       * is returned.
       */
      final Party notary =
          getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0); // METHOD 1
      // final Party notary =
      // getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")); // METHOD 2

      // Stage 1.
      progressTracker.setCurrentStep(GENERATING_TRANSACTION);
      // Generate an unsigned transaction.
      Party initiator = getOurIdentity();

      List<StateAndRef<TokenState>> inputStateAndRefTokens =
          inputTokens.stream()
              .map(this::findTokenState)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toList());

      // retrieving input tokens from vault
      if (inputStateAndRefTokens.isEmpty()) {
        throw new FlowException("Nessun token corrispondente trovato nel Vault");
      }

      // retrieving input tokens from vault
      if (inputStateAndRefTokens.size() != inputTokens.size()) {
        throw new FlowException("Numero token recuperati in Vault diverso da input");
      }

      List<Party> owners =
          inputTokens.stream()
              // Only the input holder is necessary on a Move.
              .map(TokenState::getOwner)
              // Remove duplicates as it would be an issue when initiating flows, at least.
              .collect(Collectors.toList());

      List<Party> issuers =
          inputTokens.stream().map(TokenState::getIssuer).collect(Collectors.toList());

      // recupero lista signers: holders and issuers (different from initiator)
      final List<PublicKey> allSignersKey =
          owners.stream()
              // Only the input holder is necessary on a Move.
              .map(Party::getOwningKey)
              // Remove duplicates as it would be an issue when initiating flows, at least.
              .collect(Collectors.toList());

      allSignersKey.addAll(issuers.stream().map(Party::getOwningKey).collect(Collectors.toList()));

      // command
      final Command<TokenContract.Commands.Redeem> txCommand =
          new Command<>(new TokenContract.Commands.Redeem(), allSignersKey);

      final TransactionBuilder txBuilder = new TransactionBuilder(notary).addCommand(txCommand);
      inputStateAndRefTokens.forEach(txBuilder::addInputState);

      // Stage 2.
      progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
      // Verify that the transaction is valid.
      txBuilder.verify(getServiceHub());

      // Stage 3.
      progressTracker.setCurrentStep(SIGNING_TRANSACTION);
      // Sign the transaction.
      final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

      // Stage 4.
      progressTracker.setCurrentStep(GATHERING_SIGS);
      // Send the state to the counterparty, and receive it back with their signature.
      ArrayList<Party> partyList = new ArrayList<>(owners);
      partyList.addAll(issuers);

      List<FlowSession> sessions =
          partyList.stream()
              .filter(party -> !party.equals(initiator))
              .map(this::initiateFlow)
              .collect(Collectors.toList());

      final SignedTransaction fullySignedTx =
          subFlow(
              new CollectSignaturesFlow(
                  partSignedTx, sessions, CollectSignaturesFlow.Companion.tracker()));

      // Stage 5.
      progressTracker.setCurrentStep(FINALISING_TRANSACTION);
      // Notarise and record the transaction in both parties' vaults.
      final SignedTransaction notarised = subFlow(new FinalityFlow(fullySignedTx, sessions));

      // We want our issuer to have a trace of the amounts that have been issued, whether it is a
      // holder or not,
      // in order to know the total supply. Since the issuer is not in the participants, it needs to
      // be done
      // manually. We do it after the FinalityFlow as this is the better way to do, after
      // notarisation, even if
      // here there is no notarisation.
      // TODO: in realtà questo non serve perchè nel nostro esempio l'Issuer è un partecipante
      // getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE,
      // ImmutableList.of(notarised));

      return notarised;
    }
  }

  // @InitiatedBy(Initiator.class)
  public static class Acceptor extends FlowLogic<SignedTransaction> {

    private static final Step RECEIVING_ROLE = new Step("Receiving role to impersonate.");
    private static final Step SIGNING_TRANSACTION =
        new Step("Signing transaction with our private key.") {
          @NotNull
          @Override
          public ProgressTracker childProgressTracker() {
            return SignTransactionFlow.Companion.tracker();
          }
        };
    private static final Step FINALISING_TRANSACTION =
        new Step("Obtaining notary signature and recording transaction.") {
          @NotNull
          @Override
          public ProgressTracker childProgressTracker() {
            return FinalityFlow.Companion.tracker();
          }
        };
    @NotNull private final FlowSession counterpartySession;
    @NotNull private final ProgressTracker progressTracker;

    public Acceptor(
        @NotNull final FlowSession counterpartySession) {
      this.counterpartySession = counterpartySession;
      this.progressTracker = tracker();
    }

    @NotNull
    public static ProgressTracker tracker() {
      return new ProgressTracker(RECEIVING_ROLE, SIGNING_TRANSACTION, FINALISING_TRANSACTION);
    }

    @NotNull
    @Override
    public ProgressTracker getProgressTracker() {
      return progressTracker;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {

      progressTracker.setCurrentStep(SIGNING_TRANSACTION);
      final SecureHash txId;

      final SignTransactionFlow signTransactionFlow =
          new SignTransactionFlow(counterpartySession) {

            @Override
            protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {

            }
          };
      txId = subFlow(signTransactionFlow).getId();

      progressTracker.setCurrentStep(FINALISING_TRANSACTION);
      return subFlow(new ReceiveFinalityFlow(counterpartySession, txId));
    }
  }
}
