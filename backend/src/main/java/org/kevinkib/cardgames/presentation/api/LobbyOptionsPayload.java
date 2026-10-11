package org.kevinkib.cardgames.presentation.api;

import java.util.Map;

/** Host edit of the game options of a lobby; {@code values} holds only the keys to change. */
public record LobbyOptionsPayload(String gameId, String token, Map<String, String> values) {
}
