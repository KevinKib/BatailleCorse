package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.util.List;

/** Generic per-viewer projection of a not-yet-started session (a lobby). No secrets. */
public record LobbyDto(
        boolean started,
        String gameId,
        List<LobbyPlayerDto> players,
        int hostSeat,
        int mySeat,
        int minPlayers,
        int maxPlayers,
        boolean canStart) {

    public record LobbyPlayerDto(int seat, String name, boolean joined) {
    }

    public static LobbyDto forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayerDto> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayerDto(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();

        boolean canStart = lobby.isHost(viewer) && lobby.claimedCount() >= minPlayers;

        return new LobbyDto(
                false,
                lobby.id().uuid().toString(),
                players,
                SessionGame.HOST_SEAT.id(),
                viewer.id(),
                minPlayers,
                maxPlayers,
                canStart);
    }
}
