package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;

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
 * declares at least one option. {@code seatOptionDescriptors} are the options of a computer seat; each bot
 * player carries its {@code seatOptions} (defaults filled), a human none. {@code lobbyVersion} increases with
 * every mutation of the lobby: a client ignores a view older than the one it holds. A player carries
 * {@code disconnected} while its connection is known to be down, so the host can see whom to remove.
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
        List<OptionDescriptor> seatOptionDescriptors,
        boolean canEditOptions,
        List<Integer> removableBotSeats,
        long lobbyVersion) {

    public record LobbyPlayer(int seat, String name, boolean joined, boolean bot, Map<String, String> seatOptions,
                       boolean disconnected) {
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers,
                               List<OptionDescriptor> optionDescriptors, PlayerId viewer) {
        return forViewer(lobby, minPlayers, maxPlayers, optionDescriptors, List.of(), viewer);
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers,
                               List<OptionDescriptor> optionDescriptors,
                               List<OptionDescriptor> seatOptionDescriptors, PlayerId viewer) {
        List<LobbyPlayer> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayer(seat.id().id(), seat.name(), seat.isClaimed(), seat.isBot(),
                        seatOptionsOf(seat, seatOptionDescriptors), seat.isClaimed() && seat.isDisconnected()))
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
                seatOptionDescriptors,
                host && !optionDescriptors.isEmpty(),
                removableBotSeats,
                lobby.lobbyVersion());
    }

    /** A bot's options with the game's defaults filled in; a human or free seat carries none. */
    private static Map<String, String> seatOptionsOf(SessionPlayer seat, List<OptionDescriptor> descriptors) {
        if (!seat.isClaimed() || !seat.isBot()) {
            return Map.of();
        }
        Map<String, String> options = new LinkedHashMap<>();
        descriptors.forEach(descriptor -> options.put(descriptor.key(), descriptor.defaultValue()));
        options.putAll(seat.options());
        return Map.copyOf(options);
    }
}
