package org.kevinkib.cardgames.president.presentation.dto.event;

/**
 * The events a President command can announce. {@code COMPLETION} is a play made out of turn: the completion of the
 * four. The ones the other variants need (revolution, ...) come
 * with their PRs; a trick won or a player out is carried as data of the PLAY or PASS that caused it.
 */
public enum PresidentEventType {
    START, ROUND_START, EXCHANGE, PLAY, COMPLETION, PASS, ROUND_END, READY, GAME_OVER,
    EXCHANGE_SEEN, EXCHANGE_CLOSED;

    @Override
    public String toString() {
        return name();
    }
}
