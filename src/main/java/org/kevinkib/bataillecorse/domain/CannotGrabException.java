package org.kevinkib.bataillecorse.domain;

public class CannotGrabException extends Exception {

    public CannotGrabException(Player player) {
        super("The player "+player.id()+" cannot grab this pile.");
    }

}
