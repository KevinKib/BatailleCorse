package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.Optional;

/** Tracks which transport connection currently holds which seat. */
public interface ConnectionRegistry {
    void bind(String connectionId, Seat seat);
    Optional<Seat> seatOf(String connectionId);
    Optional<Seat> unbind(String connectionId);
    void removeGame(GameId gameId);
}
