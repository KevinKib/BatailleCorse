package org.kevinkib.cardgames.presentation.api;

/** An occupant renames their own seat in a lobby; a blank name restores the default. */
public record LobbyRenamePayload(String gameId, String token, String name) {
}
