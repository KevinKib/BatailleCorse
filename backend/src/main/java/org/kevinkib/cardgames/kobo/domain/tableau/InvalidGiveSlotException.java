package org.kevinkib.cardgames.kobo.domain.tableau;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class InvalidGiveSlotException extends KoboException {

    public InvalidGiveSlotException(String message) {
        super(message);
    }
}
