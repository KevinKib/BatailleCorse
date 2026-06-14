package org.kevinkib.cardgames.presentation;

/**
 * Why a seat left the game, as classified by the session/transport layer.
 * The card-game domain never sees this — to it, both are simply a concede.
 */
public enum ForfeitReason {
    RESIGNED,
    DISCONNECTED
}
