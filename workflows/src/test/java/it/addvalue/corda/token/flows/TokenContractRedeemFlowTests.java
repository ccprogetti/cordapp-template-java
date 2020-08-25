package it.addvalue.corda.token.flows;

import com.google.common.collect.ImmutableList;
import it.addvalue.corda.token.states.TokenState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static it.addvalue.corda.token.flows.FlowHelpers.prepareMockNetworkParameters;
import static org.junit.Assert.*;

public class TokenContractRedeemFlowTests {
  private final MockNetwork network;
  private final StartedMockNode alice;
  private final StartedMockNode bob;
  private final StartedMockNode carly;
  private final StartedMockNode dan;

  public TokenContractRedeemFlowTests() throws Exception {
    network = new MockNetwork(prepareMockNetworkParameters());
    alice = network.createNode();
    bob = network.createNode();
    carly = network.createNode();
    dan = network.createNode();
    Arrays.asList(alice, bob, carly, dan)
        .forEach(
            it -> {
              it.registerInitiatedFlow(TokenContractIssueFlow.Acceptor.class);
              it.registerInitiatedFlow(
                  TokenContractRedeemFlow.Initiator.class, TokenContractRedeemFlow.Acceptor.class);
            });
  }

  @Before
  public void setup() {
    network.runNetwork();
  }

  @After
  public void tearDown() {
    network.stopNodes();
  }

  @Test(expected = IllegalArgumentException.class)
  public void inputTokensCannotBeEmpty() {
    new TokenContractRedeemFlow.Initiator(Collections.emptyList());
  }

  @Test(expected = IllegalArgumentException.class)
  public void heldQuantitiesCannotHaveAnyZeroQuantity() {
    new TokenContractRedeemFlow.Initiator(
        Arrays.asList(
            FlowHelpers.createFrom(alice, dan, -10L), FlowHelpers.createFrom(alice, bob, 20L)));
  }

  @Test
  public void signedTransactionReturnedByTheFlowIsSignedByTheIssuer() throws Throwable {

    FlowHelpers.issueTokens(alice, dan.getInfo().getLegalIdentities().get(0), network, 10L);
    FlowHelpers.issueTokens(alice, bob.getInfo().getLegalIdentities().get(0), network, 20L);

    final TokenContractRedeemFlow.Initiator flow =
        new TokenContractRedeemFlow.Initiator(
                Collections.singletonList(FlowHelpers.createFrom(alice, bob, 20L)));

    final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
    network.runNetwork();
    final SignedTransaction tx = future.get();

    tx.verifyRequiredSignatures();
  }

  @Test
  public void flowRecordsATransactionInIssuerAndHolderTransactionStoragesOnly() throws Throwable {
    FlowHelpers.issueTokens(alice, dan.getInfo().getLegalIdentities().get(0), network, 10L);
    FlowHelpers.issueTokens(alice, bob.getInfo().getLegalIdentities().get(0), network, 20L);

    final TokenContractRedeemFlow.Initiator flow =
        new TokenContractRedeemFlow.Initiator(
                Collections.singletonList(FlowHelpers.createFrom(alice, bob, 20L)));

    final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
    network.runNetwork();
    final SignedTransaction tx = future.get();

    // We check the recorded transaction in both transaction storages.
    for (StartedMockNode node : ImmutableList.of(alice, bob)) {
      assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }
    for (StartedMockNode node : ImmutableList.of(dan)) {
      assertNull(node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }
  }

  @Test
  public void recordedTransactionHasASingleInputsAndNoOutputTheTokenState() throws Throwable {
    FlowHelpers.issueTokens(alice, bob.getInfo().getLegalIdentities().get(0), network, 20L);

    final TokenContractRedeemFlow.Initiator flow =
        new TokenContractRedeemFlow.Initiator(
                Collections.singletonList(FlowHelpers.createFrom(alice, bob, 20L)));

    final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
    network.runNetwork();
    final SignedTransaction tx = future.get();

    // We check the recorded transaction in both vaults.
    for (StartedMockNode node : ImmutableList.of(alice, bob)) {
      final SignedTransaction recordedTx =
          node.getServices().getValidatedTransactions().getTransaction(tx.getId());
      assertNotNull(recordedTx);
      assertFalse(recordedTx.getTx().getInputs().isEmpty());
      final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
      assertTrue(txOutputs.isEmpty());
    }
  }

  @Test
  public void thereNoOneRecordedUnspentState() throws Throwable {

    FlowHelpers.issueTokens(alice, bob.getInfo().getLegalIdentities().get(0), network, 20L);

    final TokenContractRedeemFlow.Initiator flow =
        new TokenContractRedeemFlow.Initiator(
                Collections.singletonList(FlowHelpers.createFrom(alice, bob, 20L)));

    final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
    network.runNetwork();
    future.get();

    // We check the recorded state in both vaults.
    final List<StateAndRef<TokenState>> vaultTokens =
        alice.transaction(
            () -> {
              Vault.Page<TokenState> states =
                  alice.getServices().getVaultService().queryBy(TokenState.class);
              return states.getStates();
            });
    // there is no  unspent token
    assertTrue(vaultTokens.isEmpty());
  }
}
