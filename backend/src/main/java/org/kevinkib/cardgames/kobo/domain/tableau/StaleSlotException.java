package org.kevinkib.cardgames.kobo.domain.tableau;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class StaleSlotException extends KoboException {

    public StaleSlotException(String message) {
        super(message);
    }
}
