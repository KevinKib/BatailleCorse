package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyList;

import java.util.List;

/** The whole public game list of one game type, as sent by REST and on {@code /topic/lobbies/{gameType}}. */
public record PublicLobbyListDto(String gameType, List<PublicLobbyDto> lobbies, int total, boolean truncated,
                                 long version) {

    public static PublicLobbyListDto from(PublicLobbyList list) {
        return new PublicLobbyListDto(list.gameType(),
                list.lobbies().stream().map(PublicLobbyDto::from).toList(), list.total(), list.truncated(),
                list.version());
    }
}
