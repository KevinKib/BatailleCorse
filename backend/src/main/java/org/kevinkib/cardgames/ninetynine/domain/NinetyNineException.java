package org.kevinkib.cardgames.ninetynine.domain;

/** Base of every rejected 99 command. A rejected command never mutates the game. */
public class NinetyNineException extends Exception {

    public NinetyNineException(String message) {
        super(message);
    }
}
