package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

public enum EventType {

    CREATE,
    SEND,
    SLAP,
    GRAB;

    @Override
    public String toString() {
        return name();
    }
}
