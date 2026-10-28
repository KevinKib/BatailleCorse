package org.kevinkib.cardgames.blackjack.domain;

/** The seat is out of the game (no points left, or forfeited) and cannot act. */
public class EliminatedSeatException extends BlackjackException {

    public EliminatedSeatException() {
        super("This player is eliminated");
    }
}
