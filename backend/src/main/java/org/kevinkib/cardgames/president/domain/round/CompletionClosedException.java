package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** A completion of the four out of turn that arrived after a play or the end of the trick, or with the wrong cards. */
public class CompletionClosedException extends PresidentException {

    public CompletionClosedException() {
        super("The four can no longer be completed");
    }
}
