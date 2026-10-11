package org.kevinkib.cardgames.presentation.api;

/** Body of {@code POST /api/lobby}: which game to host and the host's display name (optional). */
public record CreateLobbyPayload(String gameType, String name) {
}
