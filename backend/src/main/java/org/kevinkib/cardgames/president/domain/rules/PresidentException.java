package org.kevinkib.cardgames.president.domain.rules;

/** Base of every rejected President command. A rejected command never mutates the game. */
public class PresidentException extends Exception {

    public PresidentException(String message) {
        super(message);
    }
}
