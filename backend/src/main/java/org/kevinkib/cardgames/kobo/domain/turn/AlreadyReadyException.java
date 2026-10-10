package org.kevinkib.cardgames.kobo.domain.turn;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;
import org.kevinkib.cardgames.game.PlayerId;

public class AlreadyReadyException extends KoboException {

    public AlreadyReadyException(PlayerId player) {
        super("Player " + player + " is already ready");
    }
}
