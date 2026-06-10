package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

public enum EventType {

    CREATE,
    SEND,
    SLAP,
    GRAB,
    JOIN,
    OPPONENT_DISCONNECTED,
    OPPONENT_RECONNECTED,
    FORFEIT;

    @Override
    public String toString() {
        return name();
    }
}
