package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Level {@code perfect}: reads the solved table of the variant ({@link MorrisSolver}). It plays the fastest
 * win if the position is won, else a move that keeps the draw, else the move that loses the slowest. Equal
 * moves are chosen through the generator. A position the solver does not know (none arises in a real game)
 * is played as the greedy level would. Rules the solver cannot handle (Six Men's Morris) are played by the
 * depth-limited {@link SearchBot} instead: no table fits in the app.
 */
public final class PerfectBot extends AbstractMorrisBot {

    private final GreedyBot fallback;
    private final SearchBot search;

    public PerfectBot(RandomGenerator random) {
        super(random);
        this.fallback = new GreedyBot(random);
        this.search = new SearchBot(random);
    }

    @Override
    MorrisDecision play(MorrisPosition position, PlayerId seat) {
        if (!position.rules().solvable()) {
            return search.play(position, seat);
        }
        MorrisSolver solver = MorrisSolver.forRules(position.rules());
        if (!solver.contains(position)) {
            return fallback.play(position, seat);
        }
        List<MorrisDecision> best = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        for (MorrisDecision decision : MorrisTransitions.legal(position)) {
            int score = score(solver, position, decision);
            if (score > bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(decision);
            }
        }
        return pick(best);
    }

    /** Higher is better: a quick win, then a draw (0), then a slow loss. */
    private static int score(MorrisSolver solver, MorrisPosition position, MorrisDecision decision) {
        MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
        if (outcome instanceof MorrisTransitions.Outcome.Win) {
            return 999;
        }
        MorrisSolver.Solution reply = solver.solve(((MorrisTransitions.Outcome.Next) outcome).position());
        return switch (reply.outcome()) {
            case LOSS -> 1_000 - (reply.depth() + 1);
            case DRAW -> 0;
            case WIN -> -1_000 + (reply.depth() + 1);
        };
    }
}
