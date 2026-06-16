package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.presentation.ForfeitReasonRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class GameCleanupServiceTest {

    /** Hand-rolled stub repository (no Mockito), recording evictStale args and returning a fixed id. */
    private static final class StubRepository implements SessionRepository {
        Duration lastFinishedGrace;
        Duration lastIdleTtl;
        final List<GameId> toEvict = new ArrayList<>();
        public void save(Game game, SessionGame s) {}
        public Game load(GameId id) { return null; }
        public org.kevinkib.cardgames.sessionmanagement.domain.SessionToken loadSessionToken(GameId i, org.kevinkib.cardgames.game.PlayerId p) { return null; }
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
        var registry = new InMemoryConnectionRegistry();
        var service = new GameCleanupService(repo, registry, new ForfeitReasonRegistry());

        var spySession = "sess-1";
        registry.bind(spySession, new org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat(
                id, new org.kevinkib.cardgames.game.PlayerId(1)));

        service.sweep();

        // The evicted game's presence entries are gone.
        assertThat(registry.seatOf(spySession).isEmpty(), org.hamcrest.Matchers.is(true));
    }

    @Test
    void whenSweep_thenUsesConfiguredThresholds() {
        var repo = new StubRepository();
        var service = new GameCleanupService(repo, new InMemoryConnectionRegistry(), new ForfeitReasonRegistry());

        service.sweep();

        assertThat(List.of(repo.lastFinishedGrace, repo.lastIdleTtl),
                contains(GameCleanupService.FINISHED_GRACE, GameCleanupService.IDLE_TTL));
    }
}
