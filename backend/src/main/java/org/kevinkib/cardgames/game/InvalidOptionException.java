package org.kevinkib.cardgames.game;

/** A game option map holds a key the game does not declare, or a value outside the permitted ones. */
public class InvalidOptionException extends RuntimeException {

    public InvalidOptionException(String message) {
        super(message);
    }
}
