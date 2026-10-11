package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.SeatClaim;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConnectionRegistry implements ConnectionRegistry {

    private final Map<String, SeatClaim> claimByConnection = new ConcurrentHashMap<>();

    @Override
    public void bind(String connectionId, SeatClaim claim) {
        claimByConnection.put(connectionId, claim);
    }

    @Override
    public Optional<SeatClaim> claimOf(String connectionId) {
        return Optional.ofNullable(claimByConnection.get(connectionId));
    }

    @Override
    public Optional<SeatClaim> unbind(String connectionId) {
        return Optional.ofNullable(claimByConnection.remove(connectionId));
    }

    @Override
    public void removeGame(GameId gameId) {
        claimByConnection.values().removeIf(claim -> claim.gameId().equals(gameId));
    }
}
