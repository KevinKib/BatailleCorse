package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;

/** Expert: iterative-deepening negamax with alpha-beta, a capture extension, a time budget and caps. */
public class ExpertDraughtsBot extends AbstractDraughtsBot {

    /** What a decision may spend. The first depth always completes, so the bot always answers. */
    public record Budget(long timeMillis, int depthCap, long nodeCap) {
        public static final Budget DEFAULT = new Budget(300, 6, 2_000_000);
    }

    /** The outcome of a search: the move, its score for the side to move, the depth completed, nodes spent. */
    public record SearchResult(DraughtsMove move, int score, int depth, long nodes) {
    }

    public ExpertDraughtsBot(RandomGenerator random, Budget budget) {
        this(random, budget, System::nanoTime);
    }

    public ExpertDraughtsBot(RandomGenerator random, Budget budget, LongSupplier nanoClock) {
    }

    public SearchResult search(DraughtsPosition position, Colour me) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    protected DraughtsMove choose(DraughtsPosition position, Colour me, List<DraughtsMove> moves) {
        throw new UnsupportedOperationException("not implemented");
    }
}
