package org.kevinkib.cardgames.mangola.presentation.dto.event;

public enum MangolaEventType {
    START, LAYOUT_CHANGED, LAYOUT_CONFIRMED, LAYOUT_WITHDRAWN, LAYOUTS_REVEALED, SOWN, GAME_OVER;

    @Override
    public String toString() {
        return name();
    }
}
