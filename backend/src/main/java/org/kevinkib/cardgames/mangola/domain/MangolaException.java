package org.kevinkib.cardgames.mangola.domain;

/** Base of every rejected command. A rejected command never mutates the game. */
public class MangolaException extends Exception {

    public MangolaException(String message) {
        super(message);
    }
}
