package it.addvalue.corda.token.contracts;

import it.addvalue.corda.token.states.TokenState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.util.*;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * TokenContract
 */
public class TokenContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = TokenContract.class.getCanonicalName();

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     *
     * @param tx
     */
    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        if (command.getValue() instanceof Commands.Issue) {

            final TokenState out = tx.outputsOfType(TokenState.class).get(0);

            requireThat(require -> {
                // (TLC): transaction level constraint Generic constraints around the transaction.
                require.using("No inputs should be consumed when issuing an token.",
                        tx.getInputs().isEmpty());
                require.using("Only one output state should be created.",
                        tx.getOutputs().size() == 1);
                require.using("The issuer and the owner cannot be the same entity.",
                        !out.getIssuer().equals(out.getOwner()));

                // (SLC): state level constraint - Token-specific constraints.
                require.using("The token value must be non-negative.",
                        out.getAmount() > 0);

                // (VC): visibility constraints
                require.using("Issuer and Owner must be participant.",
                        Arrays.asList(out.getIssuer().getOwningKey(), out.getOwner().getOwningKey()).equals(
                                out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

                // (SC): signing constraint
                require.using("Only the issuer must be signer.", Collections.singletonList(out.getIssuer().getOwningKey()).equals(command.getSigners()));
                return null;

            });

        } else if (command.getValue() instanceof Commands.Move) {
            verifyMove(tx, command);

        } else if (command.getValue() instanceof Commands.Redeem) {
            verifyRedeem(tx, command);
        } else {
            throw new RuntimeException("Not Implemented");
        }
    }

    private void verifyRedeem(LedgerTransaction tx, CommandWithParties<Commands> command) {
        final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
        final List<TokenState> outputs = tx.outputsOfType(TokenState.class);

        requireThat(require -> {
            // (TLC): transaction level constraint Generic constraints around the transaction.
            require.using("Inputs must be not be empty.", !inputs.isEmpty());

            require.using("Outputs must be empty.",outputs.isEmpty());

            // (SLC): state level constraint - Token-specific constraints.
            boolean postiveInputAmout = inputs.stream().noneMatch(tokenState -> tokenState.getAmount() < 0);
            require.using("Amount must be non-negative.",
                    postiveInputAmout);

            Set<Party> inputIssuers = inputs.stream().map(TokenState::getIssuer).distinct().collect(Collectors.toSet());
            Set<Party> outputIssuers = outputs.stream().map(TokenState::getIssuer).distinct().collect(Collectors.toSet());
            require.using("Issuers must be conserved.", inputIssuers.equals(outputIssuers));

            Map<Party, Long> inputAmout = inputs.stream().collect(Collectors.toConcurrentMap(TokenState::getIssuer, TokenState::getAmount, Math::addExact));
            Map<Party, Long> outputAmout = outputs.stream().collect(Collectors.toConcurrentMap(TokenState::getIssuer, TokenState::getAmount, Math::addExact));
            require.using("Token amount must be the same per issuer.",
                    inputAmout.equals(outputAmout));


            Set<Party> inputOwners = inputs.stream().map(TokenState::getOwner).distinct().collect(Collectors.toSet());
            require.using("Input owner must be one.",
                    inputOwners.size() == 1);

            // (SC): signing constraint
            require.using("Only the owner must be signer.", Collections.singletonList(outputs.get(0).getOwner().getOwningKey()).
                    equals(command.getSigners()));

            // (VC): visibility constraints
            //TODO


            return null;

        });

    }


    private void verifyMove(LedgerTransaction tx, CommandWithParties<Commands> command) {

        final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
        final List<TokenState> outputs = tx.outputsOfType(TokenState.class);

        requireThat(require -> {
            // (TLC): transaction level constraint Generic constraints around the transaction.
            require.using("Inputs must be not be empty.", !inputs.isEmpty());

            require.using("Outputs must be not be empty.",
                    !outputs.isEmpty());

            Set<Party> inputIssuers = inputs.stream().map(TokenState::getIssuer).distinct().collect(Collectors.toSet());
            Set<Party> outputIssuers = outputs.stream().map(TokenState::getIssuer).distinct().collect(Collectors.toSet());
            require.using("Issuers must be conserved.", inputIssuers.equals(outputIssuers));

            Map<Party, Long> inputAmout = inputs.stream().collect(Collectors.toConcurrentMap(TokenState::getIssuer, TokenState::getAmount, Math::addExact));
            Map<Party, Long> outputAmout = outputs.stream().collect(Collectors.toConcurrentMap(TokenState::getIssuer, TokenState::getAmount, Math::addExact));
            require.using("Token amount must be the same per issuer.",
                    inputAmout.equals(outputAmout));


            Set<Party> inputOwners = inputs.stream().map(TokenState::getOwner).distinct().collect(Collectors.toSet());
            require.using("Input owner must be one.",
                    inputOwners.size() == 1);


            // (SLC): state level constraint - Token-specific constraints.
            boolean postiveInputAmout = inputs.stream().noneMatch(tokenState -> tokenState.getAmount() < 0);
            boolean postiveOutputAmout = outputs.stream().noneMatch(tokenState -> tokenState.getAmount() < 0);

            require.using("Any amount must be non-negative.",
                    postiveInputAmout && postiveOutputAmout);

            // (SC): signing constraint
            require.using("Only the owner must be signer.", Collections.singletonList(outputs.get(0).getOwner().getOwningKey()).
                    equals(command.getSigners()));

            // (VC): visibility constraints
            //TODO


            return null;

        });

    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        class Issue implements Commands {
        }

        class Move implements Commands {
        }

        class Redeem implements Commands {
        }
    }
}