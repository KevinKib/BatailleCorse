package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record SessionGame(GameId id, String gameType, Map<PlayerId, SessionPlayer> players) {

    public static final PlayerId HOST_SEAT = new PlayerId(0);

    public static SessionGame create(GameId id, List<PlayerId> playerIds, String gameType) {
        Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
        for (PlayerId playerId : playerIds) {
            seats.put(playerId, new SessionPlayer(playerId, SessionToken.generate()));
        }
        return new SessionGame(id, gameType, seats);
    }

    /** Creates a session with {@code seatCount} seats numbered 0..seatCount-1. */
    public static SessionGame create(GameId id, int seatCount, String gameType) {
        return create(id, IntStream.range(0, seatCount).mapToObj(PlayerId::new).toList(), gameType);
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

    public int seatCount() {
        return players.size();
    }

    public void requestRematch(PlayerId playerId) {
        seatOrThrow(playerId).requestRematch();
    }

    /** Opt this seat out of the rematch (e.g. the player went back home). */
    public void leaveRematch(PlayerId playerId) {
        seatOrThrow(playerId).leaveRematch();
    }

    public boolean isRematchUnanimous() {
        return !players.isEmpty()
                && players.values().stream().allMatch(SessionPlayer::hasRequestedRematch);
    }

    /** Seats still in the rematch — those that have not left. */
    public int rematchStayingCount() {
        return (int) staying().count();
    }

    /** Staying seats that have asked for a rematch. */
    public int rematchReadyCount() {
        return (int) staying().filter(SessionPlayer::hasRequestedRematch).count();
    }

    /** A rematch can start once at least one seat is staying and every staying seat has asked. */
    public boolean isRematchReady() {
        List<SessionPlayer> staying = staying().toList();
        return !staying.isEmpty() && staying.stream().allMatch(SessionPlayer::hasRequestedRematch);
    }

    public void clearRematch() {
        players.values().forEach(SessionPlayer::clearRematch);
    }

    /** Seats that actually joined this game and have not opted out — the rematch participants. */
    private Stream<SessionPlayer> staying() {
        return players.values().stream()
                .filter(SessionPlayer::isClaimed)
                .filter(seat -> !seat.hasLeftRematch());
    }

    private SessionPlayer seatOrThrow(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        return seat;
    }

    public Optional<SessionToken> findTokenByPlayer(PlayerId playerId) {
        return Optional.ofNullable(players.get(playerId)).map(SessionPlayer::token);
    }

    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return players.values().stream()
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
