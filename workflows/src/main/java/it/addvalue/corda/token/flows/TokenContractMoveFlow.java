package it.addvalue.corda.token.flows;

import co.paralleluniverse.fibers.Suspendable;
import it.addvalue.corda.token.contracts.TokenContract;
import it.addvalue.corda.token.states.TokenState;
import javafx.util.Pair;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

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
public class TokenContractMoveFlow {
  @InitiatingFlow
  @StartableByRPC
  public static class Initiator extends FlowLogic<SignedTransaction> {

    @NotNull private final Pair<Party, Long> tokensToMove;

    @NotNull private final Party owner;

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

    public Initiator(
        final @NotNull Pair<Party, Long> tokensToMove, final @NotNull Party owner) {
      this.tokensToMove = tokensToMove;

      //noinspection ConstantConditions
      if (owner == null) {
        throw new NullPointerException("Owner cannot be null");
      }
      if (tokensToMove == null) {
        throw new NullPointerException("input tokens cannot be null");
      }

      if (tokensToMove.getValue() < 0) {
        throw new IllegalArgumentException("amount must be positive");
      }


      this.owner = owner;
    }

    @Override
    public ProgressTracker getProgressTracker() {
      return progressTracker;
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
      Party issuer = getOurIdentity();

      // retrieving inptu tokens from vault
      // di default recupera le unconsumed ma usiamo lo stesso il filtro
      QueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
      List<StateAndRef<TokenState>> tokenStates =
          getServiceHub().getVaultService().queryBy(TokenState.class, criteria).getStates();

      // fitro i token con owner ==  initiator (chi inizializza il flow) e per issuer l'issuer
      // richiesto in input
      List<StateAndRef<TokenState>> issuerAvailableTokens =
          tokenStates.stream()
              .filter(
                  tokenStateStateAndRef ->
                      tokenStateStateAndRef.getState().getData().getOwner().equals(issuer)
                          && tokenStateStateAndRef
                              .getState()
                              .getData()
                              .getIssuer()
                              .equals(tokensToMove.getKey()))
              .collect(Collectors.toList());

      // recupero token necessari per l'amount da trasferire (con potenziale resto da gestire)
      long tmpQuantity = 0;
      List<StateAndRef<TokenState>> inputTokens = new ArrayList<>();
      for (StateAndRef<TokenState> token : issuerAvailableTokens) {
        tmpQuantity = token.getState().getData().getAmount();
        inputTokens.add(token);
        if (tmpQuantity >= tokensToMove.getValue()) {
          break;
        }
      }

      // genero output states
      // output = input (con nuovo owner) + nuovi token con resto (owner originale)
      long finalTmpQuantity = tmpQuantity;
      List<TokenState> outputTokens =
          inputTokens.stream()
              .map(
                  tokenStateStateAndRef ->
                      new TokenState(
                          tokenStateStateAndRef.getState().getData().getIssuer(),
                          owner, tokensToMove.getValue()
                              ))
              .collect(Collectors.toList());
      // verify the difference
      if (tmpQuantity > tokensToMove.getValue()) {
        outputTokens.add(new TokenState(tokensToMove.getKey(), issuer, tmpQuantity - tokensToMove.getValue()));
      }

      // recupero tutti gli holder per i firmatari
      final List<PublicKey> allSignersKey =
          inputTokens.stream()
              // Only the input holder is necessary on a Move.
              .map(it -> it.getState().getData().getOwner().getOwningKey())
              // Remove duplicates as it would be an issue when initiating flows, at least.
              .collect(Collectors.toList());

      //command
      final Command<TokenContract.Commands.Move> txCommand =
          new Command<>(new TokenContract.Commands.Move(), allSignersKey);

      final TransactionBuilder txBuilder = new TransactionBuilder(notary).addCommand(txCommand);

      inputTokens.forEach(tokenStateStateAndRef -> txBuilder.addInputState(tokenStateStateAndRef));
      outputTokens.forEach(tokenState -> txBuilder.addOutputState(tokenState, TokenContract.ID));

      // Stage 2.
      progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
      // Verify that the transaction is valid.
      txBuilder.verify(getServiceHub());

      // Stage 3.
      progressTracker.setCurrentStep(SIGNING_TRANSACTION);
      // Sign the transaction.
      final SignedTransaction fullySignedTx = getServiceHub().signInitialTransaction(txBuilder);

      // Stage 4.
      // progressTracker.setCurrentStep(GATHERING_SIGS);
      // Send the state to the counterparty, and receive it back with their signature.
      List<FlowSession> sessions = Arrays.asList(owner, tokensToMove.getKey()).stream().map(party -> initiateFlow(party)).collect(Collectors.toList());

      //            final SignedTransaction fullySignedTx = subFlow(
      //                    new CollectSignaturesFlow(partSignedTx,
      // ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

      // Stage 5.
      progressTracker.setCurrentStep(FINALISING_TRANSACTION);
      // Notarise and record the transaction in both parties' vaults.
      final SignedTransaction notarised =
          subFlow(new FinalityFlow(fullySignedTx, sessions));

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

  @InitiatedBy(Initiator.class)
  public static class Acceptor extends FlowLogic<SignedTransaction> {

    @NotNull private final FlowSession counterpartySession;

    public Acceptor(@NotNull final FlowSession counterpartySession) {
      this.counterpartySession = counterpartySession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
      class SignTxFlow extends SignTransactionFlow {
        private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
          super(otherPartyFlow, progressTracker);
        }

        @Override
        protected void checkTransaction(SignedTransaction stx) {
          requireThat(
              require -> {
                ContractState output = stx.getTx().getOutputs().get(0).getData();
                require.using(
                    "This must be an TokenState transaction.", output instanceof TokenState);
                TokenState tokenState = (TokenState) output;
                require.using(
                    "I won't accept IOUs with a value over 100.", tokenState.getAmount() <= 100);
                return null;
              });
        }
      }
      // final SignTxFlow signTxFlow = new SignTxFlow(otherPartySession,
      // SignTransactionFlow.Companion.tracker());
      // final SecureHash txId = subFlow(signTxFlow).getId();

      return subFlow(new ReceiveFinalityFlow(counterpartySession));
    }
  }
}
