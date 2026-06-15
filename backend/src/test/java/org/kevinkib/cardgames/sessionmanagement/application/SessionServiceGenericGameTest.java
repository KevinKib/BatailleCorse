package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.time.Clock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class SessionServiceGenericGameTest {

    private final SessionService sessionService =
            new SessionService(new InMemorySessionRepository(Clock.systemUTC()), new GameFactories(java.util.List.of(new FakeGameFactory())));

    @Test
    void givenFakeGame_whenCreateAndLoad_thenWorksWithoutKnowingConcreteType() {
        Game created = sessionService.createGame("fake", 2, GameMode.SOLO, null);

        Game loaded = sessionService.getGame(created.getId());
        assertThat(loaded.getId(), is(created.getId()));
        assertThat(loaded.getPlayerIds(), hasSize(2));
    }

    @Test
    void givenFakeGame_whenCreateSolo_thenSeatsBuiltFromGamesPlayerIds() {
        Game created = sessionService.createGame("fake", 2, GameMode.SOLO, null);

        assertThat(sessionService.getSeats(created.getId()), hasSize(2));
        assertThat(sessionService.isSeatClaimed(created.getId(), new PlayerId(0)), is(true));
    }
}
