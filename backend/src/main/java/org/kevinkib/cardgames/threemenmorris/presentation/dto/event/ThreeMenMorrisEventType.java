package org.kevinkib.cardgames.threemenmorris.presentation.dto.event;

public enum ThreeMenMorrisEventType {
    START, PLACE, MOVE, GAME_OVER;

    @Override
    public String toString() {
        return name();
    }
}
