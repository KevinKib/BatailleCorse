package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.List;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.MoveGenerator;

/** Shared skeleton of the levels: the draw answer, then a choice among the legal moves. */
abstract class AbstractDraughtsBot implements DraughtsBot {

    protected final MoveGenerator generator = new MoveGenerator();

    @Override
    public DraughtsDecision decide(DraughtsPosition position, Colour me, boolean drawOfferPending) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** One of {@code moves}, which is never empty. */
    protected abstract DraughtsMove choose(DraughtsPosition position, Colour me, List<DraughtsMove> moves);
}
