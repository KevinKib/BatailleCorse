package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What the three levels share: the one rule for answering a draw offer, and the choice among equal
 * candidates through an injected generator (so a game is reproducible in tests).
 *
 * <p>Draw rule, the same at every level: on solvable rules, accept when the solver says the position is a
 * draw or a loss for the bot, decline when it is a win (or when the position is unknown to the solver); on
 * the other rules (Six Men's Morris, no table), accept unless the bot is ahead in pieces left. Bots never
 * offer a draw: the repetition rule already ends endless games.
 */
abstract class AbstractMorrisBot implements MorrisBot {

    private final RandomGenerator random;

    AbstractMorrisBot(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public final MorrisDecision decide(MorrisPosition position, PlayerId seat, boolean drawOfferPending) {
        return drawOfferPending ? answerDrawOffer(position, seat) : play(position, seat);
    }

    /** A placement or a move: there is at least one legal decision. */
    abstract MorrisDecision play(MorrisPosition position, PlayerId seat);

    final <T> T pick(List<T> candidates) {
        return candidates.get(candidates.size() == 1 ? 0 : random.nextInt(candidates.size()));
    }

    static MorrisDecision answerDrawOffer(MorrisPosition position, PlayerId seat) {
        if (!position.rules().solvable()) {
            // No solved table (Six Men's Morris): accept unless ahead in pieces left (board plus hand).
            boolean ahead = MorrisTransitions.piecesLeft(position, seat.id())
                    > MorrisTransitions.piecesLeft(position, 1 - seat.id());
            return ahead ? new MorrisDecision.DeclineDraw() : new MorrisDecision.AcceptDraw();
        }
        Optional<MorrisSolver.Solution> solution = MorrisSolver.forRules(position.rules()).find(position);
        boolean winning = solution.isPresent() && solution.get().outcome() == valueWonBy(position, seat);
        return winning ? new MorrisDecision.DeclineDraw() : new MorrisDecision.AcceptDraw();
    }

    /** The solver's outcome (for the seat to move) that means "the bot is winning". */
    private static MorrisSolver.Outcome valueWonBy(MorrisPosition position, PlayerId seat) {
        return position.toMove() == seat.id() ? MorrisSolver.Outcome.WIN : MorrisSolver.Outcome.LOSS;
    }
}
