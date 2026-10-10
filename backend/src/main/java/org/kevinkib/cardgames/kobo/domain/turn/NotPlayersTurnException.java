package org.kevinkib.cardgames.kobo.domain.turn;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;
import org.kevinkib.cardgames.game.PlayerId;

public class NotPlayersTurnException extends KoboException {

    public NotPlayersTurnException(PlayerId player) {
        super("It is not the turn of player " + player);
    }
}
