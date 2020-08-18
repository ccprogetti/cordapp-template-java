package it.addvalue.corda.token.contracts;

import it.addvalue.corda.token.states.TokenState;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;

import static java.util.Arrays.asList;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class MoveTokenContractTests {
    static private final MockServices ledgerServices = new MockServices(asList("it.addvalue.corda.token.contracts", "it.addvalue.corda.token.flow"));
    static private final TestIdentity issuer = new TestIdentity(new CordaX500Name("Issuer", "London", "GB"));
    static private final TestIdentity issuer2 = new TestIdentity(new CordaX500Name("Issuer2", "London", "GB"));

    static private final TestIdentity owner = new TestIdentity(new CordaX500Name("Owner", "London", "GB"));
    static private final TestIdentity newOwner = new TestIdentity(new CordaX500Name("NewOwner", "London", "GB"));

    static private final long amount = 1L;


    @Test
    public void transactionInputMustNotBeEmpty() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Move());
                tx.failsWith("Inputs must be not be empty.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionOutputMustNotBeEmpty() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                //tx.output(TokenContract.ID, new TokenState(issuer.getParty(), newOwner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Move());
                tx.failsWith("Outputs must be not be empty.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionInputTokenMustBeSameOutputTokenPerIssuer() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), 20L));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), newOwner.getParty(), 10L));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), 5L));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Move());
                tx.failsWith("Token amount must be the same per issuer.");
                return null;
            });
            return null;
        }));
    }


    @Test
    public void issuerMustBeConserved() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), 20L));
                tx.output(TokenContract.ID, new TokenState(issuer2.getParty(), newOwner.getParty(), 10L));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), 10L));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Move());
                tx.failsWith("Issuers must be conserved.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionInputMustHaveOneOwner() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), newOwner.getParty(), amount));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount+amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Move());
                tx.failsWith("Input owner must be one.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void anyAmountMustBePositiveAmount() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), -amount));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), newOwner.getParty(), -amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Move());
                tx.failsWith("Any amount must be non-negative.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void ownerMustSignTransaction() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), newOwner.getParty(), amount));
                tx.command(Arrays.asList(issuer.getPublicKey(), owner.getPublicKey()), new TokenContract.Commands.Move());
                tx.failsWith("Only the owner must be signer.");
                return null;
            });
            return null;
        }));
    }

}