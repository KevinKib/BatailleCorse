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
        if (drawOfferPending) {
            return DraughtsMaterial.acceptsDraw(position, me)
                    ? new DraughtsDecision.AcceptDraw()
                    : new DraughtsDecision.DeclineDraw();
        }
        if (position.toMove() != me) {
            throw new IllegalStateException("It is not the turn of " + me);
        }
        List<DraughtsMove> moves = generator.legalMoves(position);
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal move for " + me);
        }
        return new DraughtsDecision.Play(moves.size() == 1 ? moves.get(0) : choose(position, me, moves));
    }

    /** One of {@code moves}, which holds at least two moves. */
    protected abstract DraughtsMove choose(DraughtsPosition position, Colour me, List<DraughtsMove> moves);
}
