package org.kevinkib.cardgames.kobo.domain.tableau;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class EmptySlotException extends KoboException {

    public EmptySlotException(String message) {
        super(message);
    }
}
