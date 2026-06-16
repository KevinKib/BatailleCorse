package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConnectionRegistry implements ConnectionRegistry {

    private final Map<String, Seat> seatByConnection = new ConcurrentHashMap<>();

    @Override
    public void bind(String connectionId, Seat seat) {
        seatByConnection.put(connectionId, seat);
    }

    @Override
    public Optional<Seat> seatOf(String connectionId) {
        return Optional.ofNullable(seatByConnection.get(connectionId));
    }

    @Override
    public Optional<Seat> unbind(String connectionId) {
        return Optional.ofNullable(seatByConnection.remove(connectionId));
    }

    @Override
    public void removeGame(GameId gameId) {
        seatByConnection.values().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
