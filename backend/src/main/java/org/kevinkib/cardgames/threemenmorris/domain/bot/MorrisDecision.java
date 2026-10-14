package org.kevinkib.cardgames.threemenmorris.domain.bot;

/** What a bot decides to do: put a piece, move a piece, or answer a pending draw offer. */
public sealed interface MorrisDecision {

    /** Puts a piece of the hand on a free point (placement phase). */
    record Place(int point) implements MorrisDecision {
    }

    /** Moves a piece one step along a line to a free neighbouring point (movement phase). */
    record Step(int from, int to) implements MorrisDecision {
    }

    record AcceptDraw() implements MorrisDecision {
    }

    record DeclineDraw() implements MorrisDecision {
    }
}
