package org.kevinkib.cardgames.presentation.dto.event;

public enum LifecycleEventType {

    CREATE, JOIN, FORFEIT, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED, REMOVED;

    @Override
    public String toString() {
        return name();
    }
}
