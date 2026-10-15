package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** The leader of a trick must play a combination; they cannot pass. */
public class MustLeadException extends PresidentException {

    public MustLeadException() {
        super("The leader of a trick must play");
    }
}
