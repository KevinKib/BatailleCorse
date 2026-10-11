package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.SeatClaim;

import java.util.Optional;

/** Tracks which transport connection currently holds which seat token. */
public interface ConnectionRegistry {
    void bind(String connectionId, SeatClaim claim);
    Optional<SeatClaim> claimOf(String connectionId);
    Optional<SeatClaim> unbind(String connectionId);
    void removeGame(GameId gameId);
}
