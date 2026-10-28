package org.kevinkib.cardgames.draughts.presentation.dto.event;

public enum DraughtsEventType {
    START, MOVE, DRAW_OFFERED, DRAW_DECLINED, GAME_OVER;

    @Override
    public String toString() {
        return name();
    }
}
