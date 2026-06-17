package org.kevinkib.cardgames.sessionmanagement.core.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySessionRepositoryTest {

    /** Test clock whose instant we can advance. */
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advance(Duration d) { this.now = this.now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private BatailleCorse newGame(GameId id) {
        return new BatailleCorse(id, 2);
    }

    @Test
    void givenSavedGame_whenLoad_thenReturnsIt() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        var id = GameId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayerIds(), "bataille-corse"));

        assertThat(repo.load(id), is(game));
    }

    @Test
    void givenUnknownId_whenLoad_thenThrows() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> repo.load(GameId.generate()));
    }

    @Test
    void givenUnfinishedGameIdleBeyondTtl_whenEvictStale_thenRemoved() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = GameId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayerIds(), "bataille-corse"));

        clock.advance(Duration.ofMinutes(31));
        List<GameId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, contains(id));
        assertThrows(IllegalArgumentException.class, () -> repo.load(id));
    }

    @Test
    void givenUnfinishedGameWithinTtl_whenEvictStale_thenKept() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = GameId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayerIds(), "bataille-corse"));

        clock.advance(Duration.ofMinutes(10));
        List<GameId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, is(empty()));
        assertThat(repo.load(id), is(game));
    }

    @Test
    void givenFinishedGamePastGrace_whenEvictStale_thenRemovedEvenWithinIdleTtl() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = GameId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayerIds(), "bataille-corse"));
        game.forfeit(new PlayerId(0)); // now finished
        repo.touch(id);                // grace counts from here

        clock.advance(Duration.ofMinutes(3)); // > 2m grace, < 30m idle
        List<GameId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, contains(id));
    }

    @Test
    void givenTouch_whenIdleMeasured_thenResetsFromTouchInstant() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = GameId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayerIds(), "bataille-corse"));

        clock.advance(Duration.ofMinutes(20));
        repo.touch(id);                       // reset activity
        clock.advance(Duration.ofMinutes(20)); // 20m since touch, < 30m
        List<GameId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, is(empty()));
    }
}
