package org.kevinkib.cardgames.sessionmanagement.presence.port;

/**
 * Why a seat left the game, as classified by the session layer.
 * The card-game domain never sees this — to it, both are simply a concede.
 */
public enum ForfeitReason {
    RESIGNED,
    DISCONNECTED
}
