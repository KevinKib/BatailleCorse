package org.kevinkib.cardgames.kilodemerde.domain;

/** Base of every rejected Kilo de merde command. A rejected command never mutates the game. */
public class KiloDeMerdeException extends Exception {

    public KiloDeMerdeException(String message) {
        super(message);
    }
}
