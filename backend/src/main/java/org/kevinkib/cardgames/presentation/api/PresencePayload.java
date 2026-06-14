package org.kevinkib.cardgames.presentation.api;

/** Client asserts it is present at the given game seat (identified by its token). */
public record PresencePayload(String gameId, String token) {
}
