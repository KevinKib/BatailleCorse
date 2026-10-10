package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.PlayerId;

public class NotPlayersTurnException extends KoboException {

    public NotPlayersTurnException(PlayerId player) {
        super("It is not the turn of player " + player);
    }
}
