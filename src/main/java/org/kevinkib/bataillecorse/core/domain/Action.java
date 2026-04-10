package org.kevinkib.bataillecorse.core.domain;

public enum Action {

    SEND,
    SLAP,
    GRAB;

    @Override
    public String toString() {
        return name();
    }
}
