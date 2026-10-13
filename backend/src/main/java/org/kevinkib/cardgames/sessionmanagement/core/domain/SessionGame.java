package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record SessionGame(GameId id, String gameType, GameOptions options, Map<PlayerId, SessionPlayer> players,
                          AtomicLong version) {

    public SessionGame(GameId id, String gameType, GameOptions options, Map<PlayerId, SessionPlayer> players) {
        this(id, gameType, options, players, new AtomicLong());
    }

    /** Counter of the lobby's mutations; carried by every lobby view. */
    public long lobbyVersion() {
        return version.get();
    }

    /** Records one mutation of the lobby and returns the new version. */
    public long bumpVersion() {
        return version.incrementAndGet();
    }

    public static final PlayerId HOST_SEAT = new PlayerId(0);

    public static SessionGame create(GameId id, List<PlayerId> playerIds, String gameType) {
        return create(id, playerIds, gameType, GameOptions.none());
    }

    public static SessionGame create(GameId id, List<PlayerId> playerIds, String gameType, GameOptions options) {
        Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
        for (PlayerId playerId : playerIds) {
            seats.put(playerId, new SessionPlayer(playerId, SessionToken.generate()));
        }
        return new SessionGame(id, gameType, options, seats);
    }

    /** Creates a session with {@code seatCount} seats numbered 0..seatCount-1. */
    public static SessionGame create(GameId id, int seatCount, String gameType) {
        return create(id, seatCount, gameType, GameOptions.none());
    }

    public static SessionGame create(GameId id, int seatCount, String gameType, GameOptions options) {
        return create(id, IntStream.range(0, seatCount).mapToObj(PlayerId::new).toList(), gameType, options);
    }

    /** Claims a specific seat and returns it; fails if the seat is unknown or already taken. */
    public SessionPlayer claimSeat(PlayerId playerId, String name) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        if (seat.isClaimed()) {
            throw new SeatTakenException(playerId);
        }
        seat.claim(name);
        return seat;
    }

    /** Claims the host seat (seat 0) and returns it. */
    public SessionPlayer claimHost(String name) {
        return claimSeat(HOST_SEAT, name);
    }

    /** Claims the lowest-numbered free seat and returns it; the room is full if none remain. */
    public SessionPlayer claimNextFreeSeat(String name) {
        SessionPlayer free = players.values().stream()
                .filter(seat -> !seat.isClaimed())
                .min(Comparator.comparingInt(seat -> seat.id().id()))
                .orElseThrow(() -> new NoFreeSeatException(id));
        free.claim(name);
        return free;
    }

    /**
     * Claims the lowest-numbered free seat for a computer player. Bots are numbered over bots only
     * ("Bot 1", "Bot 2", ...) when no name is given.
     */
    public SessionPlayer claimBot(String name) {
        SessionPlayer free = players.values().stream()
                .filter(seat -> !seat.isClaimed())
                .min(Comparator.comparingInt(seat -> seat.id().id()))
                .orElseThrow(() -> new NoFreeSeatException(id));
        free.claimAsBot(name, botName(botSeats().size() + 1));
        return free;
    }

    /**
     * Re-seats the computer players of {@code previous}, in seat order, on the lowest free seats after the host seat,
     * with their names and seat options. Seat 0 is left for the human who reopens the room (a bot is never the host).
     */
    public void claimBotsOf(SessionGame previous) {
        for (SessionPlayer former : previous.seats()) {
            if (!former.isClaimed() || !former.isBot()) {
                continue;
            }
            SessionPlayer free = seats().stream()
                    .filter(seat -> !seat.isClaimed() && !seat.id().equals(HOST_SEAT))
                    .findFirst()
                    .orElseThrow(() -> new NoFreeSeatException(id));
            free.claimAsBot(former.name(), botName(botSeats().size() + 1));
            free.replaceOptions(former.options());
        }
    }

    /**
     * Frees a bot seat. A game is dealt to the contiguous seats 0..claimed-1, so a gap must not
     * appear below a human: removal is refused when a human sits after the bot. Bots after it shift
     * down one seat (they carry no identity) and their default names are renumbered.
     */
    public void removeBot(PlayerId playerId) {
        SessionPlayer target = seatOrThrow(playerId);
        if (!target.isClaimed() || !target.isBot()) {
            throw new NotABotSeatException(playerId);
        }
        if (!isRemovable(target)) {
            throw new BotRemovalBlockedException(playerId);
        }
        List<SessionPlayer> ordered = seats();
        for (int i = ordered.indexOf(target); i < ordered.size(); i++) {
            SessionPlayer next = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
            if (next != null && next.isClaimed()) {
                ordered.get(i).claimAsBot(next.name(), next.name());
                ordered.get(i).replaceOptions(next.options());
            } else {
                ordered.get(i).release();
                break;
            }
        }
        renumberBots();
    }

    /**
     * A human leaves the lobby. Seats stay contiguous (a game is dealt to seats 0..claimed-1): later occupants shift
     * down one seat and every seat left free gets a fresh token. Each occupant keeps its own token, name, bot flag,
     * seat options and rematch flags on its new seat. When the host (seat 0) leaves, the first human by seat order
     * takes seat 0 and becomes the host: a bot is never the host. Default names ("Player N", "Bot N") are renumbered
     * so that two seats never share one.
     */
    public void vacate(PlayerId leaver) {
        SessionPlayer leaving = seatOrThrow(leaver);
        if (!leaving.isClaimed() || leaving.isBot()) {
            throw new IllegalArgumentException("Seat " + leaver.id() + " is not held by a human");
        }
        List<SessionPlayer> staying = new ArrayList<>(seats().stream()
                .filter(seat -> seat.isClaimed() && seat != leaving)
                .toList());
        if (isHost(leaver)) {
            staying.stream().filter(seat -> !seat.isBot()).findFirst().ifPresent(newHost -> {
                staying.remove(newHost);
                staying.add(0, newHost);
            });
        }
        List<PlayerId> seatIds = seats().stream().map(SessionPlayer::id).toList();
        players.clear();
        for (int i = 0; i < seatIds.size(); i++) {
            PlayerId seatId = seatIds.get(i);
            players.put(seatId, i < staying.size()
                    ? staying.get(i).movedTo(seatId)
                    : new SessionPlayer(seatId, SessionToken.generate()));
        }
        renumberBots();
        renumberDefaultHumanNames();
    }

    /**
     * The host removes another human from the lobby. Same effect as that human leaving ({@link #vacate}): later
     * seats shift down and the freed seat gets a fresh token. Returns the token the removed human held.
     * Refused for the host seat (it leaves through Leave) and for a seat held by a bot or nobody.
     */
    public SessionToken removeHuman(PlayerId target) {
        SessionPlayer seat = seatOrThrow(target);
        if (!seat.isClaimed() || seat.isBot()) {
            throw new NotAHumanSeatException(target);
        }
        if (isHost(target)) {
            throw new CannotRemoveHostException();
        }
        SessionToken removed = seat.token();
        vacate(target);
        return removed;
    }

    /**
     * Records whether the human holding this token is connected; returns true when that changed the lobby.
     * An unknown token, a bot seat or an unchanged flag change nothing.
     */
    public boolean markConnection(SessionToken token, boolean connected) {
        return players.values().stream()
                .filter(seat -> seat.isClaimed() && !seat.isBot() && seat.token().equals(token))
                .findFirst()
                .filter(seat -> seat.isDisconnected() == connected)
                .map(seat -> {
                    seat.setDisconnected(!connected);
                    return true;
                })
                .orElse(false);
    }

    private void renumberDefaultHumanNames() {
        for (SessionPlayer seat : seats()) {
            if (seat.isClaimed() && !seat.isBot() && seat.name() != null && seat.name().matches("Player \\d+")) {
                seat.rename(seat.defaultName());
            }
        }
    }

    /** Longest player name kept; a longer one is cut. */
    public static final int MAX_NAME_LENGTH = 20;

    /**
     * Renames the human occupying a seat: trimmed, cut at {@link #MAX_NAME_LENGTH} characters, a blank name
     * restores the default "Player N".
     */
    public void renameSeat(PlayerId playerId, String name) {
        SessionPlayer seat = seatOrThrow(playerId);
        if (!seat.isClaimed() || seat.isBot()) {
            throw new IllegalArgumentException("Seat " + playerId.id() + " is not held by a human");
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH).trim();
        }
        seat.rename(trimmed.isEmpty() ? seat.defaultName() : trimmed);
    }

    /** Sets an opaque option on a computer seat; the game validates the key and value before this is called. */
    public void setSeatOption(PlayerId playerId, String key, String value) {
        SessionPlayer seat = seatOrThrow(playerId);
        if (!seat.isClaimed() || !seat.isBot()) {
            throw new NotABotSeatException(playerId);
        }
        seat.setOption(key, value);
    }

    private void renumberBots() {
        int number = 1;
        for (SessionPlayer seat : seats()) {
            if (seat.isClaimed() && seat.isBot()) {
                if (seat.name() != null && seat.name().matches("Bot \\d+")) {
                    seat.rename(botName(number));
                }
                number++;
            }
        }
    }

    private static String botName(int number) {
        return "Bot " + number;
    }

    public boolean isBot(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        return seat != null && seat.isClaimed() && seat.isBot();
    }

    public Set<PlayerId> botSeats() {
        return players.values().stream()
                .filter(seat -> seat.isClaimed() && seat.isBot())
                .map(SessionPlayer::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Bot seats that {@link #removeBot} would accept: no human holds a seat after them. */
    public List<PlayerId> removableBotSeats() {
        return seats().stream()
                .filter(seat -> seat.isClaimed() && seat.isBot() && isRemovable(seat))
                .map(SessionPlayer::id)
                .toList();
    }

    private boolean isRemovable(SessionPlayer bot) {
        return players.values().stream()
                .noneMatch(seat -> seat.isClaimed() && !seat.isBot() && seat.id().id() > bot.id().id());
    }

    public boolean isHost(PlayerId playerId) {
        return HOST_SEAT.equals(playerId);
    }

    public boolean isClaimed(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        return seat != null && seat.isClaimed();
    }

    public int claimedCount() {
        return (int) players.values().stream().filter(SessionPlayer::isClaimed).count();
    }

    private SessionPlayer seatOrThrow(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        return seat;
    }

    /** The token of a claimed human seat; a free seat or a bot seat has none to give. */
    public Optional<SessionToken> findTokenByPlayer(PlayerId playerId) {
        return Optional.ofNullable(players.get(playerId))
                .filter(seat -> seat.isClaimed() && !seat.isBot())
                .map(SessionPlayer::token);
    }

    /** Resolves a claimed human seat; a free seat's token or a bot's internal token resolves to nothing. */
    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return players.values().stream()
                .filter(seat -> seat.isClaimed() && !seat.isBot())
                .filter(seat -> seat.token().equals(token))
                .map(SessionPlayer::id)
                .findFirst();
    }

    /** Seats ordered by player id, for presentation. */
    public List<SessionPlayer> seats() {
        return players.values().stream()
                .sorted(Comparator.comparing(seat -> seat.id().id()))
                .toList();
    }
}
