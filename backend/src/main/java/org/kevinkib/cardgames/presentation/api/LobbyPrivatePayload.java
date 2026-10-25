package org.kevinkib.cardgames.presentation.api;

/** The host hides the lobby from the public game list ({@code privateGame}) or lists it again. */
public record LobbyPrivatePayload(String gameId, String token, boolean privateGame) {
}
