package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.List;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;

/** Easy: a uniformly random legal move. */
public class RandomDraughtsBot extends AbstractDraughtsBot {

    private final RandomGenerator random;

    public RandomDraughtsBot(RandomGenerator random) {
        this.random = random;
    }

    @Override
    protected DraughtsMove choose(DraughtsPosition position, Colour me, List<DraughtsMove> moves) {
        throw new UnsupportedOperationException("not implemented");
    }
}
