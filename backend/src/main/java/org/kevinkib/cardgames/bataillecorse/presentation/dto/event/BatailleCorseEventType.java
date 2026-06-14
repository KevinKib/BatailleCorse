package org.kevinkib.cardgames.bataillecorse.presentation.dto.event;

public enum BatailleCorseEventType {

    SEND, SLAP, GRAB;

    @Override
    public String toString() {
        return name();
    }
}
