package it.addvalue.corda.token.states;

import it.addvalue.corda.token.contracts.TokenContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * TokenState
 */
@BelongsToContract(TokenContract.class)
public final class TokenState implements ContractState {

    @NotNull
    private final Party issuer;
    @NotNull
    private final Party owner;

    private final long amount;

    public TokenState(@NotNull final Party issuer, @NotNull final Party owner, final long amount) {
        //noinspection ConstantConditions
        if (issuer == null) {
            throw new NullPointerException("issuer cannot be null");
        }

        //noinspection ConstantConditions
        if (owner == null) {
            throw new NullPointerException("owner cannot be null");
        }

        this.issuer = issuer;
        this.owner = owner;
        this.amount = amount;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(issuer, owner);
    }

    // Forgetting equals and hashcode will cause all sorts of nasty side effects, as we are likely to put instances
    // in Sets or HashMaps. You always want to be able to know whether 2 instances are the same anyway.

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TokenState that = (TokenState) o;
        return amount == that.amount &&
                issuer.equals(that.issuer) &&
                owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, owner, amount);
    }

    @Override
    public String toString() {
        return "TokenState{" +
                "issuer=" + issuer +
                ", owner=" + owner +
                ", amount=" + amount +
                '}';
    }

    @NotNull
    public Party getIssuer() {
        return issuer;
    }

    @NotNull
    public Party getOwner() {
        return owner;
    }

    public long getAmount() {
        return amount;
    }
}