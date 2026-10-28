package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Medium: one ply. The pit that leaves it the most seeds on its own half (that is, the biggest capture), then the
 * most pits left to sow, then at random. A sowing that never ends (a draw, when the rules allow it) is played
 * only when nothing else is left.
 *
 * <p>Its layout is drawn uniformly from the placement book's list for its side ({@link MangolaPlacementBook}, frozen
 * from the placement study); a refused layout is drawn again without it, then the base fallback applies.
 */
public final class GreedyMangolaBot extends AbstractMangolaBot {

    public GreedyMangolaBot(RandomGenerator random) {
        super(random);
    }

    @Override
    protected int[] proposeLayout(MangolaRules rules, Side side, Set<List<Integer>> rejected) {
        return fromBook(rules, side, rejected, false);
    }

    @Override
    protected int choose(MangolaPosition position, Side side, List<MangolaMoves.Move> moves) {
        List<MangolaMoves.Move> finite = moves.stream().filter(move -> !move.endless()).toList();
        List<MangolaMoves.Move> candidates = finite.isEmpty() ? moves : finite;
        List<MangolaMoves.Move> best = new ArrayList<>();
        long bestScore = Long.MIN_VALUE;
        for (MangolaMoves.Move move : candidates) {
            long score = score(move, side, position.rules());
            if (score > bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(move);
            }
        }
        return best.get(random.nextInt(best.size())).pit();
    }

    /** Seeds kept on its half, then the pits it could sow next. */
    private static long score(MangolaMoves.Move move, Side side, MangolaRules rules) {
        long seeds = move.board().sideTotal(side);
        long mobility = move.endless() ? 0 : MangolaMoves.mobility(move.board(), side, rules);
        return seeds * 100 + mobility;
    }
}
