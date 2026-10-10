package org.kevinkib.cardgames.kobo.domain;


public class NothingToMatchException extends KoboException {

    public NothingToMatchException() {
        super("The discard pile is empty");
    }
}
