package org.kevinkib.bataillecorse.domain;

public class CannotGrabException extends Exception {

    public CannotGrabException(Player player) {
        super("The player "+player.getId()+" cannot grab this pile.");
    }

}
