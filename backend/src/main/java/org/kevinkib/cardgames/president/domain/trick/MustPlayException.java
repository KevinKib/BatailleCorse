package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** A pass while compulsory play is on and the player holds a legal play (a completion is never compulsory). */
public class MustPlayException extends PresidentException {

    public MustPlayException() {
        super("You must play: a legal play exists");
    }
}
