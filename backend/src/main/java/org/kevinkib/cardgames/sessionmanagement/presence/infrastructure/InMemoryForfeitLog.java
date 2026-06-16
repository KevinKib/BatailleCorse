package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryForfeitLog implements ForfeitLog {

    private final Map<Seat, ForfeitReason> reasonBySeat = new ConcurrentHashMap<>();

    @Override
    public void record(Seat seat, ForfeitReason reason) {
        reasonBySeat.put(seat, reason);
    }

    @Override
    public Map<Integer, ForfeitReason> reasonsBySeat(GameId gameId) {
        Map<Integer, ForfeitReason> result = new HashMap<>();
        reasonBySeat.forEach((seat, reason) -> {
            if (seat.gameId().equals(gameId)) {
                result.put(seat.playerId().id(), reason);
            }
        });
        return result;
    }

    @Override
    public void removeGame(GameId gameId) {
        reasonBySeat.keySet().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
