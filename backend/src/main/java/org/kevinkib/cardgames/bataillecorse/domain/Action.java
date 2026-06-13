package org.kevinkib.cardgames.bataillecorse.domain;

public enum Action {

    SEND,
    SLAP,
    GRAB;

    @Override
    public String toString() {
        return name();
    }
}
