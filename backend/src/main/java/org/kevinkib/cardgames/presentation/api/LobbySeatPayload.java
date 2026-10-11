package org.kevinkib.cardgames.presentation.api;

/** Host request about one seat of a lobby, such as removing the bot sitting there. */
public record LobbySeatPayload(String gameId, String token, int seat) {
}
