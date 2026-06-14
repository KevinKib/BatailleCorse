package org.kevinkib.cardgames.presentation.dto.event;

public enum EventType {

    CREATE,
    SEND,
    SLAP,
    GRAB,
    JOIN,
    OPPONENT_DISCONNECTED,
    OPPONENT_RECONNECTED,
    FORFEIT,
    REMATCH;

    @Override
    public String toString() {
        return name();
    }
}
