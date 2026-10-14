package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.random.RandomGenerator;

/** Level {@code random}: a uniformly random legal placement or move. */
public final class RandomBot extends AbstractMorrisBot {

    public RandomBot(RandomGenerator random) {
        super(random);
    }

    @Override
    MorrisDecision play(MorrisPosition position, PlayerId seat) {
        return pick(MorrisTransitions.legal(position));
    }
}
