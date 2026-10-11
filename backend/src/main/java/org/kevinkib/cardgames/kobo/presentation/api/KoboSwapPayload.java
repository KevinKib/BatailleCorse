package org.kevinkib.cardgames.kobo.presentation.api;

/** Blind swap: one of the actor slots against a card of another player. */
public record KoboSwapPayload(String gameId, String token, Integer ownSlot, Integer seat, Integer slot) {
}
