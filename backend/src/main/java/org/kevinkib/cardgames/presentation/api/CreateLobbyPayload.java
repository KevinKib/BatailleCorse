package org.kevinkib.cardgames.presentation.api;

/**
 * Body of {@code POST /api/lobby}: which game to host, the host's display name (optional) and whether the lobby is
 * hidden from the public game list from the start ({@code privateGame}, absent means public).
 */
public record CreateLobbyPayload(String gameType, String name, Boolean privateGame) {

    public CreateLobbyPayload(String gameType, String name) {
        this(gameType, name, null);
    }
}
