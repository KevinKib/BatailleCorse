package org.kevinkib.cardgames.presentation.api;

/** Host sets one option (for example a tier) on the computer seat {@code seat} of a lobby. */
public record LobbySeatOptionPayload(String gameId, String token, int seat, String key, String value) {
}
