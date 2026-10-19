package org.kevinkib.cardgames.president.presentation.api;

/** A seat's command about one round of a game; {@code round} is required by the endpoints that use it. */
public record PresidentRoundPayload(String gameId, String token, Integer round) {
}
