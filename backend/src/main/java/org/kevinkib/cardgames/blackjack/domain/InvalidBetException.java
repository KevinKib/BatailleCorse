package org.kevinkib.cardgames.blackjack.domain;

/** The bet is below the minimum, above the cap (the largest bet or the stack) or not a whole number of points. */
public class InvalidBetException extends BlackjackException {

    public InvalidBetException(int amount, int min, int max) {
        super("A bet of " + amount + " is not allowed (from " + min + " to " + max + ")");
    }
}
