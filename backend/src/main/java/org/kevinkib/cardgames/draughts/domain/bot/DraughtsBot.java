package org.kevinkib.cardgames.draughts.domain.bot;

import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;

/** A computer player. Candidates must come from the move generator, so a bot never plays an illegal move. */
public interface DraughtsBot {

    /**
     * Decides what the bot playing {@code me} does now: a move when it is {@code me}'s turn, or the answer to a
     * pending draw offer.
     */
    DraughtsDecision decide(DraughtsPosition position, Colour me, boolean drawOfferPending);
}
