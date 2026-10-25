package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobby;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyState;

/** A public game as published: no token, no seat and no timestamp. */
public record PublicLobbyDto(String gameId, String gameType, String hostName, int joined, int bots, int maxPlayers,
                             PublicLobbyState state) {

    public static PublicLobbyDto from(PublicLobby lobby) {
        return new PublicLobbyDto(lobby.gameId(), lobby.gameType(), lobby.hostName(), lobby.joined(), lobby.bots(),
                lobby.maxPlayers(), lobby.state());
    }
}
