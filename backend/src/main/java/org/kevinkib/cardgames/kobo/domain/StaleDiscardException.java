package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class StaleDiscardException extends KoboException {

    public StaleDiscardException() {
        super("The top of the discard pile is not the one the player saw");
    }
}
