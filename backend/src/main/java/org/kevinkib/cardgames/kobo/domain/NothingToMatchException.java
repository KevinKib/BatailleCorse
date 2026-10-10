package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class NothingToMatchException extends KoboException {

    public NothingToMatchException() {
        super("The discard pile is empty");
    }
}
