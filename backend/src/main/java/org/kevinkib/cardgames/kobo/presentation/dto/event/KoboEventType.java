package org.kevinkib.cardgames.kobo.presentation.dto.event;

public enum KoboEventType {
    START, READY, DRAW, SWAP, DISCARD_DRAWN, PEEK, BLIND_SWAP, KING_SWAP, SKIP_POWER, END_TURN,
    KOBO, MATCH, GIVE_TEN, ROUND_END, GAME_OVER;

    @Override
    public String toString() {
        return name();
    }
}
