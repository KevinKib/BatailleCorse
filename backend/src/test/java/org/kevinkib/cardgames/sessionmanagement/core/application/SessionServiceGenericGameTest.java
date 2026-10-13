package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.presentation.support.StartedRooms;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class SessionServiceGenericGameTest {

    private final SessionService sessionService =
            new SessionService(new InMemorySessionRepository(Clock.systemUTC()), new GameFactories(java.util.List.of(new FakeGameFactory())));

    @Test
    void givenFakeGame_whenCreateAndLoad_thenWorksWithoutKnowingConcreteType() {
        Game created = StartedRooms.start(sessionService, "fake", 2);

        Game loaded = sessionService.getGame(created.getId());
        assertThat(loaded.getId(), is(created.getId()));
        assertThat(loaded.getPlayerIds(), hasSize(2));
    }

    @Test
    void givenFakeGame_whenStarted_thenSeatsHeldByTheHumansWhoJoined() {
        Game created = StartedRooms.start(sessionService, "fake", 2);

        assertThat(sessionService.isSeatClaimed(created.getId(), new PlayerId(1)), is(true));
        assertThat(sessionService.isSeatClaimed(created.getId(), new PlayerId(0)), is(true));
    }
}
