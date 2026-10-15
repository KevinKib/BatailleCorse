package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** A valid play that is neither a lead nor a higher play of the same count than the top of the trick. */
public class PlayDoesNotBeatException extends PresidentException {

    public PlayDoesNotBeatException(String message) {
        super(message);
    }
}
