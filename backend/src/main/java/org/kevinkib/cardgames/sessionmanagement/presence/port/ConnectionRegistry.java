package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.Optional;
import java.util.Set;

/** Tracks which transport connection currently holds which seat. */
public interface ConnectionRegistry {
    void bind(String connectionId, Seat seat);
    Optional<Seat> seatOf(String connectionId);
    Optional<Seat> unbind(String connectionId);
    void removeGame(GameId gameId);

    /** Player ids of every seat currently bound to a live connection for this game. */
    Set<PlayerId> seatsFor(GameId gameId);
}
