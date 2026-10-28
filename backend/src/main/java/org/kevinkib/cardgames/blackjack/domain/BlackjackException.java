package org.kevinkib.cardgames.blackjack.domain;

/** Base of every rejected Blackjack command. A rejected command never mutates the game. */
public class BlackjackException extends Exception {

    public BlackjackException(String message) {
        super(message);
    }
}
