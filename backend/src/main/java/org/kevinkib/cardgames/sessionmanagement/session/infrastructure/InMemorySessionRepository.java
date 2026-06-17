package org.kevinkib.cardgames.sessionmanagement.session.infrastructure;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.session.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.session.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.session.domain.SessionToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRepository implements SessionRepository {

    private final Clock clock;
    private final Map<GameId, Game> games = new ConcurrentHashMap<>();
    private final Map<GameId, SessionGame> sessionGames = new ConcurrentHashMap<>();
    private final Map<GameId, Instant> lastActivityAt = new ConcurrentHashMap<>();

    public InMemorySessionRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(Game game, SessionGame sessionGame) {
        GameId id = game.getId();
        games.put(id, game);
        sessionGames.put(id, sessionGame);
        lastActivityAt.put(id, clock.instant());
    }

    @Override
    public Game load(GameId id) {
        Game game = games.get(id);
        if (game == null) {
            throw new IllegalArgumentException("Unknown game " + id);
        }
        return game;
    }

    @Override
    public SessionToken loadSessionToken(GameId gameId, PlayerId playerId) {
        return loadSessionGame(gameId)
                .findTokenByPlayer(playerId)
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionGame loadSessionGame(GameId id) {
        SessionGame sessionGame = sessionGames.get(id);
        if (sessionGame == null) {
            throw new IllegalArgumentException("Unknown game " + id);
        }
        return sessionGame;
    }

    @Override
    public void saveLobby(SessionGame sessionGame) {
        GameId id = sessionGame.id();
        sessionGames.put(id, sessionGame);
        lastActivityAt.put(id, clock.instant());
    }

    @Override
    public Optional<Game> findGame(GameId id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public void touch(GameId id) {
        if (games.containsKey(id) || sessionGames.containsKey(id)) {
            lastActivityAt.put(id, clock.instant());
        }
    }

    @Override
    public void remove(GameId id) {
        games.remove(id);
        sessionGames.remove(id);
        lastActivityAt.remove(id);
    }

    @Override
    public List<GameId> evictStale(Duration finishedGrace, Duration idleTtl) {
        Instant now = clock.instant();
        List<GameId> evicted = new ArrayList<>();
        for (GameId id : sessionGames.keySet()) {
            Instant last = lastActivityAt.getOrDefault(id, Instant.EPOCH);
            Duration idle = Duration.between(last, now);
            Game game = games.get(id);
            Duration threshold = (game != null && game.isFinished()) ? finishedGrace : idleTtl;
            if (idle.compareTo(threshold) >= 0) {
                evicted.add(id);
            }
        }
        evicted.forEach(this::remove);
        return evicted;
    }
}
