package org.kevinkib.cardgames.presentation.dto.event;

public enum LifecycleEventType {

    CREATE, JOIN, FORFEIT, REMATCH, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED;

    @Override
    public String toString() {
        return name();
    }
}
