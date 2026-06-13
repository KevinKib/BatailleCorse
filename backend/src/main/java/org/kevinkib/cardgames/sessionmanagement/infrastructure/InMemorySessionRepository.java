package org.kevinkib.cardgames.sessionmanagement.infrastructure;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRepository implements SessionRepository {

    private final Clock clock;
    private final Map<BatailleCorseId, BatailleCorse> games = new ConcurrentHashMap<>();
    private final Map<BatailleCorseId, SessionGame> sessionGames = new ConcurrentHashMap<>();
    private final Map<BatailleCorseId, Instant> lastActivityAt = new ConcurrentHashMap<>();

    public InMemorySessionRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(BatailleCorse batailleCorse, SessionGame sessionGame) {
        BatailleCorseId id = batailleCorse.getId();
        games.put(id, batailleCorse);
        sessionGames.put(id, sessionGame);
        lastActivityAt.put(id, clock.instant());
    }

    @Override
    public BatailleCorse load(BatailleCorseId id) {
        BatailleCorse game = games.get(id);
        if (game == null) {
            throw new IllegalArgumentException("Unknown game " + id);
        }
        return game;
    }

    @Override
    public SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId) {
        return loadSessionGame(batailleCorseId)
                .findTokenByPlayer(playerId)
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionGame loadSessionGame(BatailleCorseId id) {
        SessionGame sessionGame = sessionGames.get(id);
        if (sessionGame == null) {
            throw new IllegalArgumentException("Unknown game " + id);
        }
        return sessionGame;
    }

    @Override
    public void touch(BatailleCorseId id) {
        if (games.containsKey(id)) {
            lastActivityAt.put(id, clock.instant());
        }
    }

    @Override
    public void remove(BatailleCorseId id) {
        games.remove(id);
        sessionGames.remove(id);
        lastActivityAt.remove(id);
    }

    @Override
    public List<BatailleCorseId> evictStale(Duration finishedGrace, Duration idleTtl) {
        Instant now = clock.instant();
        List<BatailleCorseId> evicted = new ArrayList<>();
        for (Map.Entry<BatailleCorseId, BatailleCorse> entry : games.entrySet()) {
            BatailleCorseId id = entry.getKey();
            Instant last = lastActivityAt.getOrDefault(id, Instant.EPOCH);
            Duration idle = Duration.between(last, now);
            Duration threshold = entry.getValue().isFinished() ? finishedGrace : idleTtl;
            if (idle.compareTo(threshold) >= 0) {
                evicted.add(id);
            }
        }
        evicted.forEach(this::remove);
        return evicted;
    }
}
