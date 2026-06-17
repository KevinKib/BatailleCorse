package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.util.List;

/** Generic per-viewer projection of a not-yet-started session (a lobby). Published; no secrets. */
public record LobbyView(
        boolean started,
        String gameId,
        List<LobbyPlayer> players,
        int hostSeat,
        int mySeat,
        int minPlayers,
        int maxPlayers,
        boolean canStart) {

    public record LobbyPlayer(int seat, String name, boolean joined) {
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayer> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayer(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();

        boolean canStart = lobby.isHost(viewer) && lobby.claimedCount() >= minPlayers;

        return new LobbyView(
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
