package org.kevinkib.cardgames.threemenmorris.presentation.dto.event;

public enum ThreeMenMorrisEventType {
    START, PLACE, MOVE, REMOVE, DRAW_OFFERED, DRAW_DECLINED, GAME_OVER;

    @Override
    public String toString() {
        return name();
    }
}
