package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-owned record of which seat forfeited a game and why. Reused across
 * games (and, in future, other game types) so the reason can be merged into
 * game state. Cleared per game on eviction, mirroring StompSessionSeatRegistry.
 */
public class ForfeitReasonRegistry {

    private final Map<Seat, ForfeitReason> reasonBySeat = new ConcurrentHashMap<>();

    public void record(Seat seat, ForfeitReason reason) {
        reasonBySeat.put(seat, reason);
    }

    /** Seat-index -> reason for the given game (empty if no seat forfeited). */
    public Map<Integer, ForfeitReason> reasonsBySeat(GameId gameId) {
        Map<Integer, ForfeitReason> result = new HashMap<>();
        reasonBySeat.forEach((seat, reason) -> {
            if (seat.gameId().equals(gameId)) {
                result.put(seat.playerId().id(), reason);
            }
        });
        return result;
    }

    public void removeGame(GameId gameId) {
        reasonBySeat.keySet().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
