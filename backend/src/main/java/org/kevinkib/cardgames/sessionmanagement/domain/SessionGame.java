package org.kevinkib.cardgames.sessionmanagement.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SessionGame(GameId id, Map<PlayerId, SessionPlayer> players) {

    public static SessionGame create(GameId id, List<Player> players) {
        Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
        for (Player player : players) {
            seats.put(player.id(), new SessionPlayer(player.id(), SessionToken.generate()));
        }
        return new SessionGame(id, seats);
    }

    public void claim(PlayerId playerId, String name) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        seat.claim(name);
    }

    public boolean isClaimed(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        return seat != null && seat.isClaimed();
    }

    public void requestRematch(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        seat.requestRematch();
    }

    public boolean isRematchUnanimous() {
        return !players.isEmpty()
                && players.values().stream().allMatch(SessionPlayer::hasRequestedRematch);
    }

    public void clearRematch() {
        players.values().forEach(SessionPlayer::clearRematch);
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
