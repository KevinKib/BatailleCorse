package org.kevinkib.cardgames.sessionmanagement.session.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.session.domain.SessionGame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class InMemorySessionRepositoryLobbyTest {

    private SessionGame lobbyOf(GameId id) {
        SessionGame lobby = SessionGame.create(id, List.of(new PlayerId(0), new PlayerId(1)), "fake");
        lobby.claimSeat(new PlayerId(0), "Host");
        return lobby;
    }

    @Test
    void givenSavedLobby_whenFindGame_thenEmpty() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        GameId id = GameId.generate();
        repo.saveLobby(lobbyOf(id));

        assertThat(repo.findGame(id).isPresent(), is(false));
        assertThat(repo.loadSessionGame(id).isClaimed(new PlayerId(0)), is(true));
    }

    @Test
    void givenStartedGame_whenFindGame_thenPresent() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        GameId id = GameId.generate();
        repo.saveLobby(lobbyOf(id));
        repo.save(new FakeGame(id, 2), lobbyOf(id));

        assertThat(repo.findGame(id).isPresent(), is(true));
    }

    @Test
    void givenStaleLobby_whenEvictStale_thenLobbyEvicted() {
        Instant t0 = Instant.parse("2026-06-16T00:00:00Z");
        var clock = Clock.fixed(t0, ZoneOffset.UTC);
        var repo = new InMemorySessionRepository(clock);
        GameId id = GameId.generate();
        repo.saveLobby(lobbyOf(id));

        List<GameId> evicted = repo.evictStale(Duration.ZERO, Duration.ZERO);

        assertThat(evicted, contains(id));
    }
}
