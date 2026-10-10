package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.util.List;
import java.util.Map;

/**
 * Generic per-viewer projection of a not-yet-started session (a lobby). Published; no secrets, and
 * in particular no token (a bot seat's token never leaves the backend).
 *
 * <p>{@code removableBotSeats} is filled for the host only: the bot seats the host may remove now.
 */
public record LobbyView(
        boolean started,
        String gameId,
        List<LobbyPlayer> players,
        int hostSeat,
        int mySeat,
        int minPlayers,
        int maxPlayers,
        boolean canStart,
        Map<String, String> options,
        List<Integer> removableBotSeats) {

    public record LobbyPlayer(int seat, String name, boolean joined, boolean bot) {
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayer> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayer(seat.id().id(), seat.name(), seat.isClaimed(), seat.isBot()))
                .toList();

        boolean host = lobby.isHost(viewer);
        boolean canStart = host && lobby.claimedCount() >= minPlayers;
        List<Integer> removableBotSeats = host
                ? lobby.removableBotSeats().stream().map(PlayerId::id).toList()
                : List.of();

        return new LobbyView(
                false,
                lobby.id().uuid().toString(),
                players,
                SessionGame.HOST_SEAT.id(),
                viewer.id(),
                minPlayers,
                maxPlayers,
                canStart,
                lobby.options().values(),
                removableBotSeats);
    }
}
