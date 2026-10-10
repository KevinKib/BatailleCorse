package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record SessionGame(GameId id, String gameType, GameOptions options, Map<PlayerId, SessionPlayer> players) {

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

    /** Claims every seat (used when a single owner controls all players, e.g. solo mode). */
    public void claimAllSeats() {
        players.values().forEach(seat -> seat.claim(null));
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
            } else {
                ordered.get(i).release();
                break;
            }
        }
        renumberBots();
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

    public void requestRematch(PlayerId playerId) {
        seatOrThrow(playerId).requestRematch();
    }

    /**
     * Every expected player has asked for the rematch. Expected players are the human seats that
     * took part and have not left for good: bots never click, and an unclaimed seat has nobody to
     * wait for.
     */
    public boolean isRematchUnanimous() {
        List<SessionPlayer> expected = players.values().stream()
                .filter(seat -> seat.isClaimed() && !seat.isBot() && !seat.hasLeftRematch())
                .toList();
        return !expected.isEmpty() && expected.stream().allMatch(SessionPlayer::hasRequestedRematch);
    }

    /** The player will not take part in the rematch any more; the remaining players stop waiting. */
    public void leaveRematch(PlayerId playerId) {
        SessionPlayer seat = seatOrThrow(playerId);
        if (seat.isClaimed() && !seat.isBot()) {
            seat.leaveRematch();
        }
    }

    /**
     * A rematch deals one game to seats 0..k-1 (seat id = player index), so it can start only if at
     * least {@code minPlayers} seats remain and the departed ones sit after all the others.
     */
    public boolean isRematchPlayable(int minPlayers) {
        int remaining = 0;
        boolean gap = false;
        for (SessionPlayer seat : seats()) {
            boolean stays = seat.isClaimed() && !seat.hasLeftRematch();
            if (stays && gap) {
                return false;
            }
            if (stays) {
                remaining++;
            } else {
                gap = true;
            }
        }
        return remaining >= minPlayers;
    }

    /** Frees the seats of players who left, so the rematch is dealt to those who stay. */
    public void releaseDepartedSeats() {
        players.values().stream().filter(SessionPlayer::hasLeftRematch).forEach(SessionPlayer::release);
    }

    public void clearRematch() {
        players.values().forEach(SessionPlayer::clearRematch);
    }

    private SessionPlayer seatOrThrow(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        return seat;
    }

    /** The token of a human seat; a bot seat's token never leaves the session, so it is not found. */
    public Optional<SessionToken> findTokenByPlayer(PlayerId playerId) {
        return Optional.ofNullable(players.get(playerId))
                .filter(seat -> !seat.isBot())
                .map(SessionPlayer::token);
    }

    /** Resolves a human seat; a bot seat can never be impersonated, even with its internal token. */
    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return players.values().stream()
                .filter(seat -> !seat.isBot())
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
