package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** The player already said they are ready for the next round. */
public class AlreadyReadyException extends PresidentException {

    public AlreadyReadyException() {
        super("You are already ready");
    }
}
