package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StompSessionSeatRegistry {

    private final Map<String, Seat> seatBySession = new ConcurrentHashMap<>();

    public void bind(String sessionId, Seat seat) {
        seatBySession.put(sessionId, seat);
    }

    public Optional<Seat> seatOf(String sessionId) {
        return Optional.ofNullable(seatBySession.get(sessionId));
    }

    public Optional<Seat> unbind(String sessionId) {
        return Optional.ofNullable(seatBySession.remove(sessionId));
    }

    public void removeGame(GameId gameId) {
        seatBySession.values().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
