package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * What the levels share: the acceptance of a layout (legal, not already refused, not made of endless pits only)
 * with a deterministic fallback on the catalogue, so that the layout decision always ends, even with a constant
 * generator, and never repeats a layout the game refused.
 */
abstract class AbstractMangolaBot implements MangolaBot {

    private static final int PROPOSALS = 4;

    protected final RandomGenerator random;

    AbstractMangolaBot(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public final int[] decideLayout(MangolaRules rules, Side side, Collection<List<Integer>> rejected) {
        Set<List<Integer>> refused = Set.copyOf(rejected);
        for (int proposal = 0; proposal < PROPOSALS; proposal++) {
            int[] layout = proposeLayout(rules, side, refused);
            if (acceptable(rules, side, layout, refused)) {
                return layout;
            }
        }
        List<int[]> catalogue = MangolaLayouts.catalogue();
        int start = random.nextInt(catalogue.size());
        for (int i = 0; i < catalogue.size(); i++) {
            int[] layout = catalogue.get((start + i) % catalogue.size()).clone();
            if (acceptable(rules, side, layout, refused)) {
                return layout;
            }
        }
        // Everything was refused (cannot happen with a handful of refusals): any random layout will do.
        return MangolaLayouts.random(random);
    }

    /** A candidate layout; the base class checks it and asks again or falls back on the catalogue. */
    protected abstract int[] proposeLayout(MangolaRules rules, Side side, Set<List<Integer>> rejected);

    protected final boolean acceptable(MangolaRules rules, Side side, int[] layout, Set<List<Integer>> rejected) {
        List<Integer> boxed = MangolaLayouts.boxed(layout);
        return !rejected.contains(boxed) && !Mangola.onlyEndlessMoves(rules, side, boxed);
    }

    /** A random or a pattern layout, half and half. */
    protected final int[] randomOrPattern() {
        if (random.nextInt(2) == 0) {
            return MangolaLayouts.random(random);
        }
        List<int[]> catalogue = MangolaLayouts.catalogue();
        return catalogue.get(random.nextInt(catalogue.size())).clone();
    }

    @Override
    public final int decideMove(MangolaPosition position, Side side) {
        List<MangolaMoves.Move> moves = MangolaMoves.moves(MangolaMoves.boardOf(position), side, position.rules());
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal pit for side " + side);
        }
        return choose(position, side, moves);
    }

    /** Picks among at least one legal move. */
    protected abstract int choose(MangolaPosition position, Side side, List<MangolaMoves.Move> moves);
}
