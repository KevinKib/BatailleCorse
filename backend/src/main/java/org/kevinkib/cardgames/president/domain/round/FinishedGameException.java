package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/**
 * The round is over, so no card can be played. Until P2 adds the match, a round is the whole game.
 */
public class FinishedGameException extends PresidentException {

    public FinishedGameException() {
        super("The round is over");
    }
}
