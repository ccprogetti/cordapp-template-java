package it.addvalue.corda.token.flows;

import com.google.common.collect.ImmutableList;
import it.addvalue.corda.token.states.TokenState;
import javafx.util.Pair;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class TokenContractMoveFlowTests {
  // private final long amount = 10L;
  private MockNetwork network;
  private StartedMockNode mover;
  private StartedMockNode acceptor;
  private StartedMockNode issuer;

  @Before
  public void setup() {
    network =
        new MockNetwork(
            new MockNetworkParameters()
                .withCordappsForAllNodes(
                    ImmutableList.of(
                        TestCordapp.findCordapp("it.addvalue.corda.token.contracts"),
                        TestCordapp.findCordapp("it.addvalue.corda.token.flows"))));

    mover = network.createPartyNode(new CordaX500Name("Mover", "London", "GB"));
    acceptor = network.createPartyNode(new CordaX500Name("Acceptor", "London", "GB"));
    issuer = network.createPartyNode(new CordaX500Name("Issuer", "London", "GB"));

    // For real nodes this happens automatically, but we have to manually register the flow for
    // tests.
    for (StartedMockNode node : ImmutableList.of(mover, acceptor, issuer)) {
      node.registerInitiatedFlow(TokenContractMoveFlow.Acceptor.class);
    }
    network.runNetwork();
  }

  @After
  public void tearDown() {
    network.stopNodes();
  }

  private void issueToken(long amount, Party owner)
      throws ExecutionException, InterruptedException {

    TokenContractIssueFlow.Initiator issueFlow =
        new TokenContractIssueFlow.Initiator(amount, owner);
    CordaFuture<SignedTransaction> issueFuture = issuer.startFlow(issueFlow);
    network.runNetwork();
    issueFuture.get();
  }

  @Test(expected = IllegalArgumentException.class)
  public void flowRejectsInvalidTokens() throws Exception {
    Pair<Party, Long> tokensToMove =
        new Pair<Party, Long>(mover.getInfo().getLegalIdentities().get(0), -10L);

    // The TokenContract specifies that amount cannot have negative values.
    TokenContractMoveFlow.Initiator flow =
        new TokenContractMoveFlow.Initiator(
            tokensToMove, acceptor.getInfo().getLegalIdentities().get(0));
  }

  @Test(expected = IllegalArgumentException.class)
  public void amyTokenAmountMovedMustBePositve() throws Exception {
    Pair<Party, Long> tokensToMove =
        new Pair<Party, Long>(mover.getInfo().getLegalIdentities().get(0), -10L);

    // The TokenContract specifies that amount cannot have negative values.
    TokenContractMoveFlow.Initiator flow =
        new TokenContractMoveFlow.Initiator(
            tokensToMove, acceptor.getInfo().getLegalIdentities().get(0));
  }

  @Test
  public void signedTransactionReturnedByTheFlowIsSignedByAllTheOwners() throws Exception {
    Pair<Party, Long> tokensToMove =
        new Pair<Party, Long>(issuer.getInfo().getLegalIdentities().get(0), 10L);

    // issue token to move for mover
    issueToken(10, mover.getInfo().getLegalIdentities().get(0));

    TokenContractMoveFlow.Initiator flow =
        new TokenContractMoveFlow.Initiator(
            tokensToMove, acceptor.getInfo().getLegalIdentities().get(0));
    CordaFuture<SignedTransaction> future = mover.startFlow(flow);
    network.runNetwork();
    SignedTransaction signedTx = future.get();

    // issuer signature
    signedTx.verifyRequiredSignatures();
  }

  @Test
  public void flowRecordsATransactionInAllPartiesTransactionStorages() throws Exception {
    Pair<Party, Long> tokensToMove =
        new Pair<Party, Long>(issuer.getInfo().getLegalIdentities().get(0), 10L);

    // issue token to move for mover
    issueToken(10, mover.getInfo().getLegalIdentities().get(0));

    TokenContractMoveFlow.Initiator flow =
        new TokenContractMoveFlow.Initiator(
            tokensToMove, acceptor.getInfo().getLegalIdentities().get(0));
    CordaFuture<SignedTransaction> future = mover.startFlow(flow);
    network.runNetwork();
    SignedTransaction signedTx = future.get();

    // We check the recorded transaction in both vaults.
    for (StartedMockNode node : ImmutableList.of(issuer, mover, acceptor)) {
      assertEquals(
          signedTx, node.getServices().getValidatedTransactions().getTransaction(signedTx.getId()));
    }
  }

  @Test
  public void flowRecordsTheCorrectTokenStateInBothPartiesVaults() throws Exception {
    Pair<Party, Long> tokenToMove =
        new Pair<Party, Long>(issuer.getInfo().getLegalIdentities().get(0), 10L);

    // issue token to move for mover
    issueToken(20L, mover.getInfo().getLegalIdentities().get(0));

    TokenContractMoveFlow.Initiator flow =
        new TokenContractMoveFlow.Initiator(
            tokenToMove, acceptor.getInfo().getLegalIdentities().get(0));
    CordaFuture<SignedTransaction> future = mover.startFlow(flow);
    network.runNetwork();
    future.get();

    // We check the recorded State in both vaults.


    List<StateAndRef<TokenState>> issuerStates =
        issuer.getServices().getVaultService().queryBy(TokenState.class).getStates();
    List<StateAndRef<TokenState>> moverStates =
        mover.getServices().getVaultService().queryBy(TokenState.class).getStates();
    List<StateAndRef<TokenState>> acceptorStates =
        acceptor.getServices().getVaultService().queryBy(TokenState.class).getStates();

    //issuer è un participant in entrambi gli states di output
    assertEquals(2, issuerStates.size());
    //mover vede solo lo state in in cui è participant
    assertEquals(1, moverStates.size());
    //acceptor vede solo lo state in in cui è participant
    assertEquals(1, acceptorStates.size());

    //lo states su vault del mover abbia come owner il mover stesso
    assertEquals(moverStates.get(0).getState().getData().getOwner(), mover.getInfo().getLegalIdentities().get(0));
  }
}
