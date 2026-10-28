package org.kevinkib.cardgames.draughts.domain.bot;

import org.kevinkib.cardgames.draughts.domain.DraughtsMove;

/** What a bot decides: play a move, or answer a draw offer. */
public sealed interface DraughtsDecision {

    record Play(DraughtsMove move) implements DraughtsDecision {
    }

    record AcceptDraw() implements DraughtsDecision {
    }

    record DeclineDraw() implements DraughtsDecision {
    }
}
