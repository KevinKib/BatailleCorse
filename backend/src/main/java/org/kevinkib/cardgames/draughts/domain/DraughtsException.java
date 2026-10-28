package org.kevinkib.cardgames.draughts.domain;

/**
 * Base of every rejected command. A rejected command never mutates the game. The message is a locale key
 * ({@code draughts.errors.*}), never text to show: the client renders the key.
 */
public class DraughtsException extends Exception {

    public DraughtsException(String code) {
        super(code);
    }
}
