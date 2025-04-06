package org.kevinkib.bataillecorse.domain;

public class PlayerCannotPlayException extends Exception {

    public PlayerCannotPlayException() {
        super("The current player cannot play.");
    }

}
