package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** The command comes from a seat that is not the one to act. */
public class NotPlayersTurnException extends PresidentException {

    public NotPlayersTurnException() {
        super("It is not your turn");
    }
}
