package org.kevinkib.cardgames.sessionmanagement.core.application;

/** State of a listed game: {@code OPEN} has a free seat and no game yet, {@code FULL} no free seat, {@code IN_PLAY} a started game. */
public enum PublicLobbyState {
    OPEN,
    FULL,
    IN_PLAY
}
