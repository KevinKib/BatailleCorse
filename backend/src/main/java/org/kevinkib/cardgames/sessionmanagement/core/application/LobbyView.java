package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic per-viewer projection of a not-yet-started session (a lobby). Published; no secrets, and
 * in particular no token (a bot seat's token never leaves the backend).
 *
 * <p>{@code removableBotSeats} is filled for the host only: the bot seats the host may remove now.
 * {@code optionDescriptors} are the game's declared options, {@code options} their current values
 * (defaults filled for missing keys) and {@code canEditOptions} is true for the host when the game
 * declares at least one option.
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
        List<OptionDescriptor> optionDescriptors,
        boolean canEditOptions,
        List<Integer> removableBotSeats) {

    public record LobbyPlayer(int seat, String name, boolean joined, boolean bot) {
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers,
                               List<OptionDescriptor> optionDescriptors, PlayerId viewer) {
        List<LobbyPlayer> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayer(seat.id().id(), seat.name(), seat.isClaimed(), seat.isBot()))
                .toList();

        boolean host = lobby.isHost(viewer);
        boolean canStart = host && lobby.claimedCount() >= minPlayers;
        List<Integer> removableBotSeats = host
                ? lobby.removableBotSeats().stream().map(PlayerId::id).toList()
                : List.of();

        Map<String, String> options = new LinkedHashMap<>();
        optionDescriptors.forEach(descriptor -> options.put(descriptor.key(), descriptor.defaultValue()));
        options.putAll(lobby.options().values());

        return new LobbyView(
                false,
                lobby.id().uuid().toString(),
                players,
                SessionGame.HOST_SEAT.id(),
                viewer.id(),
                minPlayers,
                maxPlayers,
                canStart,
                Map.copyOf(options),
                optionDescriptors,
                host && !optionDescriptors.isEmpty(),
                removableBotSeats);
    }
}
