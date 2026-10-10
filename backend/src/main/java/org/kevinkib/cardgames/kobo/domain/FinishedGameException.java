package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class FinishedGameException extends KoboException {

    public FinishedGameException() {
        super("The game is finished");
    }
}
