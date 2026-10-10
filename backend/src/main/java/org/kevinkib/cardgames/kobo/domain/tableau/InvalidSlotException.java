package org.kevinkib.cardgames.kobo.domain.tableau;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class InvalidSlotException extends KoboException {

    public InvalidSlotException(String message) {
        super(message);
    }
}
