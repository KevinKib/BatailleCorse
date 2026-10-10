package org.kevinkib.cardgames.kobo.domain;


public class StaleDiscardException extends KoboException {

    public StaleDiscardException() {
        super("The top of the discard pile is not the one the player saw");
    }
}
