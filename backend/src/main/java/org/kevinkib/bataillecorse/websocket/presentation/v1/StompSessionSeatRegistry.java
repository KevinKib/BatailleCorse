package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
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

    public void removeGame(BatailleCorseId gameId) {
        seatBySession.values().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
