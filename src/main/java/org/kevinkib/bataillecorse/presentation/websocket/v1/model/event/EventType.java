package org.kevinkib.bataillecorse.presentation.websocket.v1.model.event;

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
