package org.kevinkib.cardgames.kobo.domain;

/** Base of every rejected Kobo command. A rejected command never mutates the game. */
public class KoboException extends Exception {

    public KoboException(String message) {
        super(message);
    }
}
