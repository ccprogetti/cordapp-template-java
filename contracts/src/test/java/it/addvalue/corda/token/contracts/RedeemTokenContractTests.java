package it.addvalue.corda.token.contracts;

import it.addvalue.corda.token.states.TokenState;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;

import static java.util.Arrays.asList;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class RedeemTokenContractTests {
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
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Redeem());
                tx.failsWith("Inputs must be not be empty.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionOutputMustBeEmpty() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.output(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Redeem());
                tx.failsWith("Outputs must be empty.");
                return null;
            });
            return null;
        }));
    }


    @Test
    public void amountMustBePositive() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), -amount));
                tx.command(issuer.getPublicKey(), new TokenContract.Commands.Redeem());
                tx.failsWith("Amount must be non-negative.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void ownerAndIssuerMustSignTransaction() {
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(TokenContract.ID, new TokenState(issuer.getParty(), owner.getParty(), amount));
                tx.command(Arrays.asList(owner.getPublicKey()), new TokenContract.Commands.Redeem());
                tx.failsWith("Owner and Issuer must be signer.");
                return null;
            });
            return null;
        }));
    }

}