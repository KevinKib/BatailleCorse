package org.kevinkib.cardgames.threemenmorris.domain;

/** Base of every rejected command. A rejected command never mutates the game. */
public class ThreeMenMorrisException extends Exception {

    public ThreeMenMorrisException(String message) {
        super(message);
    }
}
