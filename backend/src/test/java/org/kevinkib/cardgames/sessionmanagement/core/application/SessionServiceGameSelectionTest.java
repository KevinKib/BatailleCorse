package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.presentation.support.StartedRooms;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class SessionServiceGameSelectionTest {

    private final SessionService service = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()),
            new GameFactories(List.of(new BatailleCorseFactory(), new BullshitFactory())));

    @Test
    void givenBullshitSlug_whenCreateGame_thenHostsABullshit() {
        Game game = StartedRooms.start(service, "bullshit", 3);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(service.getGame(game.getId()), instanceOf(Bullshit.class));
    }

    @Test
    void givenBatailleCorseSlug_whenCreateGame_thenHostsABatailleCorse() {
        Game game = StartedRooms.start(service, "bataille-corse", 2);

        assertThat(game, instanceOf(BatailleCorse.class));
    }
}
