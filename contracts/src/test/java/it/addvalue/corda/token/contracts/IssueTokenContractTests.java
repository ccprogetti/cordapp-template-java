package it.addvalue.corda.token.contracts;

import it.addvalue.corda.token.states.TokenState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class IssueTokenContractTests {
    static private final MockServices ledgerServices = new MockServices(asList("it.addvalue.corda.token.contracts", "it.addvalue.corda.token.flow"));
    static private final TestIdentity issuer = new TestIdentity(new CordaX500Name("Issuer", "London", "GB"));
    static private final TestIdentity owner = new TestIdentity(new CordaX500Name("Owner", "London", "GB"));
    static private final int amount = 1;

    @Test
    public void transactionMustIncludeIssueCommand() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                //tx.fails();
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Issue());
                tx.verifies();
                return null;
            });
            return null;
        }));
    }


    @Test
    public void transactionMustHaveNoInput() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Issue());
                tx.failsWith("No inputs should be consumed when issuing an token.");
                return null;
            });
            return null;
        }));
    }


    @Test
    public void transactionMustHaveOneOutput() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Issue());
                tx.failsWith("Only one output state should be created.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void issuerIsNotOwner() {
        final TestIdentity fakeOwner = new TestIdentity(issuer.getName(), issuer.getKeyPair());
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), fakeOwner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Issue());
                tx.failsWith("The issuer and the owner cannot be the same entity.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void cannotCreateNegativeAmount() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), -amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Issue());
                tx.failsWith("The token value must be non-negative.");
                return null;
            });
            return null;
        }));
    }

    //TODO: la lista dei participant sullo state non si può modificare così implementata. Ovvero la lista è sempre generata dal metodo get e dipende direttamente dalle property issuer ed owner.
    //TODO: nonostante il caso di test sia impossibile lascio il controllo sulla classe contarct.
    @Test
    public void issuerAndOwnerMustBeParticipant() {
        final TestIdentity other = new TestIdentity(new CordaX500Name("other", "London", "GB"));
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                TokenState state = new TokenState(issuer.getParty(), owner.getParty(), amount);
                state.getParticipants().set(0, other.getParty());
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Issue());
                tx.failsWith("Issuer and Owner must be participant.");
                return null;
            });
            return null;
        }));
    }


    @Test
    public void issuerMustSignTransaction() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(Arrays.asList(owner.getPublicKey(), issuer.getPublicKey()), new TokenContract.Commands.Issue());
                tx.failsWith("Only the issuer must be signer.");
                return null;
            });
            return null;
        }));
    }

}