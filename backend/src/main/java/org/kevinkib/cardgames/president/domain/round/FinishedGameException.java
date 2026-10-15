package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** The match is over, so no command can change it. */
public class FinishedGameException extends PresidentException {

    public FinishedGameException() {
        super("The game is over");
    }
}
