package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceEvictionCleanup;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class GameCleanupServiceTest {

    /** Hand-rolled stub repository (no Mockito), recording evictStale args and returning a fixed id. */
    private static final class StubRepository implements SessionRepository {
        Duration lastFinishedGrace;
        Duration lastIdleTtl;
        final List<GameId> toEvict = new ArrayList<>();
        public void save(Game game, SessionGame s) {}
        public Game load(GameId id) { return null; }
        public org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken loadSessionToken(GameId i, org.kevinkib.cardgames.game.PlayerId p) { return null; }
        public SessionGame loadSessionGame(GameId id) { return null; }
        public void touch(GameId id) {}
        public void remove(GameId id) {}
        public void saveLobby(SessionGame s) {}
        public Optional<Game> findGame(GameId id) { return Optional.empty(); }
        public List<GameId> evictStale(Duration finishedGrace, Duration idleTtl) {
            this.lastFinishedGrace = finishedGrace;
            this.lastIdleTtl = idleTtl;
            return new ArrayList<>(toEvict);
        }
    }

    @Test
    void givenEvictedGames_whenSweep_thenPresenceCleared() {
        var repo = new StubRepository();
        var id = GameId.generate();
        repo.toEvict.add(id);
        var connections = new InMemoryConnectionRegistry();
        var forfeits = new InMemoryForfeitLog();
        var service = new GameCleanupService(repo, List.of(new PresenceEvictionCleanup(connections, forfeits)));

        connections.bind("sess-1", new Seat(id, new PlayerId(1)));

        service.sweep();

        assertThat(connections.seatOf("sess-1").isEmpty(), is(true));
    }

    @Test
    void whenSweep_thenUsesConfiguredThresholds() {
        var repo = new StubRepository();
        var service = new GameCleanupService(repo, List.of());

        service.sweep();

        assertThat(List.of(repo.lastFinishedGrace, repo.lastIdleTtl),
                contains(GameCleanupService.FINISHED_GRACE, GameCleanupService.IDLE_TTL));
    }
}
