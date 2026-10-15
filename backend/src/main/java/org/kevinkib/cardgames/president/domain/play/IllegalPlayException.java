package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** The cards do not form a play: none, several ranks, or a card that cannot be played. */
public class IllegalPlayException extends PresidentException {

    public IllegalPlayException(String message) {
        super(message);
    }
}
