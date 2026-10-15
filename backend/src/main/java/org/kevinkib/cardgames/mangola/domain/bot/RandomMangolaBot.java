package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Easy: a uniformly random legal pit, a random layout. */
public final class RandomMangolaBot extends AbstractMangolaBot {

    public RandomMangolaBot(RandomGenerator random) {
        super(random);
    }

    @Override
    protected int[] proposeLayout(MangolaRules rules, Side side, Set<List<Integer>> rejected) {
        return MangolaLayouts.random(random);
    }

    @Override
    protected int choose(MangolaPosition position, Side side, List<MangolaMoves.Move> moves) {
        return moves.get(random.nextInt(moves.size())).pit();
    }
}
