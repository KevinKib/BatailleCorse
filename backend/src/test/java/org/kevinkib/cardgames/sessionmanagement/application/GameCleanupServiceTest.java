package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.presentation.ForfeitReasonRegistry;
import org.kevinkib.cardgames.presentation.StompSessionSeatRegistry;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class GameCleanupServiceTest {

    /** Hand-rolled stub repository (no Mockito), recording evictStale args and returning a fixed id. */
    private static final class StubRepository implements SessionRepository {
        Duration lastFinishedGrace;
        Duration lastIdleTtl;
        final List<BatailleCorseId> toEvict = new ArrayList<>();
        public void save(BatailleCorse b, SessionGame s) {}
        public BatailleCorse load(BatailleCorseId id) { return null; }
        public org.kevinkib.cardgames.sessionmanagement.domain.SessionToken loadSessionToken(BatailleCorseId i, org.kevinkib.cardgames.bataillecorse.domain.PlayerId p) { return null; }
        public SessionGame loadSessionGame(BatailleCorseId id) { return null; }
        public void touch(BatailleCorseId id) {}
        public void remove(BatailleCorseId id) {}
        public List<BatailleCorseId> evictStale(Duration finishedGrace, Duration idleTtl) {
            this.lastFinishedGrace = finishedGrace;
            this.lastIdleTtl = idleTtl;
            return new ArrayList<>(toEvict);
        }
    }

    @Test
    void givenEvictedGames_whenSweep_thenPresenceCleared() {
        var repo = new StubRepository();
        var id = BatailleCorseId.generate();
        repo.toEvict.add(id);
        var registry = new StompSessionSeatRegistry();
        var service = new GameCleanupService(repo, registry, new ForfeitReasonRegistry());

        var spySession = "sess-1";
        registry.bind(spySession, new org.kevinkib.cardgames.presentation.Seat(
                id, new org.kevinkib.cardgames.bataillecorse.domain.PlayerId(1)));

        service.sweep();

        // The evicted game's presence entries are gone.
        assertThat(registry.seatOf(spySession).isEmpty(), org.hamcrest.Matchers.is(true));
    }

    @Test
    void whenSweep_thenUsesConfiguredThresholds() {
        var repo = new StubRepository();
        var service = new GameCleanupService(repo, new StompSessionSeatRegistry(), new ForfeitReasonRegistry());

        service.sweep();

        assertThat(List.of(repo.lastFinishedGrace, repo.lastIdleTtl),
                contains(GameCleanupService.FINISHED_GRACE, GameCleanupService.IDLE_TTL));
    }
}
