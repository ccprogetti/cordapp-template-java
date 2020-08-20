package it.addvalue.corda.token.states;


import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestIdentity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


public class TokenStateTests {

    private final Party issuer = new TestIdentity(new CordaX500Name("issuer", "London", "GB")).getParty();
    private final Party owner = new TestIdentity(new CordaX500Name("owner", "London", "GB")).getParty();


    /**
     * SLC (State Level Constraints): issuer must be not null
     */
    @Test(expected = NullPointerException.class)
    public void doesNotAcceptNullIssuer() {
        //noinspection ConstantConditions
        new TokenState(null, owner, 1L);
    }

    /**
     * SLC (State Level Constraints): owner must be not null
     */
    @Test(expected = NullPointerException.class)
    public void doesNotAcceptNullOwner() {
        //noinspection ConstantConditions
        new TokenState(issuer, null, 1L);
    }

    @Test
    public void constructorAndGettersAreWorking() {
        final TokenState state = new TokenState(issuer, owner, 2L);
        assertEquals(issuer, state.getIssuer());
        assertEquals(owner, state.getOwner());
        assertEquals(2L, state.getAmount());
    }

    @Test
    public void equalsAndHashcodeIdentifyIdenticalInstances() {
        final TokenState token1 = new TokenState(issuer, owner, 2L);
        final TokenState token2 = new TokenState(issuer, owner, 2L);
        assertEquals(token1, token2);
        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeIdentifyDifferentInstances() {
        final TokenState token1 = new TokenState(owner, owner, 2L);
        final TokenState token2 = new TokenState(issuer, owner, 2L);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }


}
