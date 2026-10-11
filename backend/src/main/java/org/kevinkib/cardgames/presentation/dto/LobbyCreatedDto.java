package org.kevinkib.cardgames.presentation.dto;

/** Answer of {@code POST /api/lobby}: the room id and the host's seat token, for the caller only. */
public record LobbyCreatedDto(String gameId, String token) {
}
