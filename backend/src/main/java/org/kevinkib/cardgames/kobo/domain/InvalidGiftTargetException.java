package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class InvalidGiftTargetException extends KoboException {

    public InvalidGiftTargetException(String message) {
        super(message);
    }
}
