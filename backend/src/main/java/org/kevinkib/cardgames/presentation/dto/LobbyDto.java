package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;

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

    private static final int HOST_SEAT = 0;

    public static LobbyDto forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayerDto> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayerDto(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();

        long claimed = lobby.seats().stream().filter(seat -> seat.isClaimed()).count();
        boolean viewerIsHost = viewer.id() == HOST_SEAT;
        boolean canStart = viewerIsHost && claimed >= minPlayers;

        return new LobbyDto(
                false,
                lobby.id().uuid().toString(),
                players,
                HOST_SEAT,
                viewer.id(),
                minPlayers,
                maxPlayers,
                canStart);
    }
}
