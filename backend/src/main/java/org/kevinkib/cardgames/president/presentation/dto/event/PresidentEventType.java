package org.kevinkib.cardgames.president.presentation.dto.event;

/**
 * The events a President command can announce. The ones the variants need (completion, revolution, ...) come
 * with their PRs; a trick won or a player out is carried as data of the PLAY or PASS that caused it.
 */
public enum PresidentEventType {
    START, ROUND_START, EXCHANGE, PLAY, PASS, ROUND_END, READY, GAME_OVER;

    @Override
    public String toString() {
        return name();
    }
}
