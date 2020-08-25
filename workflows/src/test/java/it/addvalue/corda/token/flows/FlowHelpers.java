package it.addvalue.corda.token.flows;

import com.google.common.collect.ImmutableList;
import it.addvalue.corda.token.states.TokenState;
import javafx.util.Pair;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

interface FlowHelpers {

    @NotNull
    static MockNetworkParameters prepareMockNetworkParameters() throws Exception {
        return new MockNetworkParameters()
                .withNotarySpecs(ImmutableList.of(new MockNetworkNotarySpec(Constants.desiredNotary)))
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("it.addvalue.corda.token.contracts"),
                        TestCordapp.findCordapp("it.addvalue.corda.token.flows"))
                );
    }

    @NotNull
    static TokenState createFrom(
            @NotNull final StartedMockNode issuer,
            @NotNull final StartedMockNode holder,
            final long quantity) {
        return new TokenState(
                issuer.getInfo().getLegalIdentities().get(0),
                holder.getInfo().getLegalIdentities().get(0),
                quantity);
    }

    @NotNull
    static Pair<Party, Long> toPair(@NotNull final TokenState token) {
        return new Pair<>(token.getOwner(), token.getAmount());
    }

    static void assertHasStatesInVault(
            @NotNull final StartedMockNode node,
            @NotNull final List<TokenState> tokenStates) {
        final List<StateAndRef<TokenState>> vaultTokens = node.transaction(() -> {

            Vault.Page<TokenState> states = node.getServices().getVaultService().queryBy(TokenState.class);

            System.out.println(states.getStatesMetadata().toString());

            return states.getStates();
        });
        assertEquals(tokenStates.size(), vaultTokens.size());
        for (int i = 0; i < tokenStates.size(); i++) {
            // The equals and hashcode functions are implemented correctly.
            assertEquals(vaultTokens.get(i).getState().getData(), tokenStates.get(i));
        }
    }

    class NodeHolding {
        @NotNull
        public final StartedMockNode holder;
        public final long quantity;

        public NodeHolding(@NotNull final StartedMockNode holder, final long quantity) {
            this.holder = holder;
            this.quantity = quantity;
        }

        @NotNull
        public Pair<Party, Long> toPair() {
            return new Pair<>(holder.getInfo().getLegalIdentities().get(0), quantity);
        }
    }

    @NotNull
    static List<StateAndRef<TokenState>> issueTokens(
            @NotNull final StartedMockNode issuerNode,
            @NotNull final Party owner,
            @NotNull final MockNetwork network,
            final long amount)
            throws Throwable {
        final TokenContractIssueFlow.Initiator flow = new TokenContractIssueFlow.Initiator(amount, owner);
        final CordaFuture<SignedTransaction> future = issuerNode.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();
        return tx.toLedgerTransaction(issuerNode.getServices())
                .outRefsOfType(TokenState.class);
    }

}
