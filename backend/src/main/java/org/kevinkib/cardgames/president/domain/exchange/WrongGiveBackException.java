package org.kevinkib.cardgames.president.domain.exchange;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** A return that does not fit the exchange: nothing to return for this seat, or the wrong number of cards. */
public class WrongGiveBackException extends PresidentException {

    public WrongGiveBackException(String message) {
        super(message);
    }
}
