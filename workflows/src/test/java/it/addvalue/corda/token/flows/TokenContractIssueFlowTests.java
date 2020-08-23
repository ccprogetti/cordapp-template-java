package it.addvalue.corda.token.flows;

import com.google.common.collect.ImmutableList;
import it.addvalue.corda.token.states.TokenState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TokenContractIssueFlowTests {
    private MockNetwork network;
    private StartedMockNode issuer;
    private StartedMockNode owner;

    @Before
    public void setup() {
        network = new MockNetwork(new MockNetworkParameters().withCordappsForAllNodes(ImmutableList.of(
                TestCordapp.findCordapp("it.addvalue.corda.token.contracts"),
                TestCordapp.findCordapp("it.addvalue.corda.token.flows"))));
        issuer = network.createPartyNode(null);
        owner = network.createPartyNode(null);
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        for (StartedMockNode node : ImmutableList.of(issuer, owner)) {
            node.registerInitiatedFlow(TokenContractIssueFlow.Acceptor.class);
        }
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test(expected = IllegalArgumentException.class)
    public void flowRejectsInvalidTokens() throws Exception {
        // The TokenContract specifies that amount cannot have negative values.
        TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(-1, owner.getInfo().getLegalIdentities().get(0));
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheInitiator() throws Exception {
        TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(10, owner.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();

        SignedTransaction signedTx = future.get();
        signedTx.verifySignaturesExcept(owner.getInfo().getLegalIdentities().get(0).getOwningKey());
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheAcceptor() throws Exception {
        TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(1, owner.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();

        SignedTransaction signedTx = future.get();
        signedTx.verifySignaturesExcept(issuer.getInfo().getLegalIdentities().get(0).getOwningKey());
    }

    @Test
    public void flowRecordsATransactionInBothPartiesTransactionStorages() throws Exception {
        TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(1, owner.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(issuer, owner)) {
            assertEquals(signedTx, node.getServices().getValidatedTransactions().getTransaction(signedTx.getId()));
        }
    }

    @Test
    public void recordedTransactionHasNoInputsAndASingleOutputTheInputToken() throws Exception {
        long amount = 1;
        TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(amount, owner.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(issuer, owner)) {
            SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());
            List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assert (txOutputs.size() == 1);
            assert (recordedTx.getTx().getInputs().isEmpty());

            TokenState recordedState = (TokenState) txOutputs.get(0).getData();
            assertEquals(recordedState.getAmount(), amount);
            assertEquals(recordedState.getIssuer(), issuer.getInfo().getLegalIdentities().get(0));
            assertEquals(recordedState.getOwner(), owner.getInfo().getLegalIdentities().get(0));
        }
    }

    @Test
    public void flowRecordsTheCorrectTokenStateInBothPartiesVaults() throws Exception {
        long amount = 1;
        TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(1, owner.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded State in both vaults.
        for (StartedMockNode node : ImmutableList.of(issuer, owner)) {
            node.transaction(() -> {
                List<StateAndRef<TokenState>> states = node.getServices().getVaultService().queryBy(TokenState.class).getStates();
                assertEquals(1, states.size());
                TokenState recordedState = states.get(0).getState().getData();
                assertEquals(recordedState.getAmount(), amount);
                assertEquals(recordedState.getIssuer(), issuer.getInfo().getLegalIdentities().get(0));
                assertEquals(recordedState.getOwner(), owner.getInfo().getLegalIdentities().get(0));
                return null;
            });
        }
    }
}