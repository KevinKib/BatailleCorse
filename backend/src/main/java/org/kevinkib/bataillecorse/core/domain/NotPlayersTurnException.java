package org.kevinkib.bataillecorse.core.domain;

public class NotPlayersTurnException extends Exception {

    public NotPlayersTurnException(Player player) {
        super("It is not the turn of player "+player.id()+".");
    }

}
