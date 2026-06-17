package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
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
        Game game = service.createGame("bullshit", 3, GameMode.SOLO);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(service.getGame(game.getId()), instanceOf(Bullshit.class));
    }

    @Test
    void givenBatailleCorseSlug_whenCreateGame_thenHostsABatailleCorse() {
        Game game = service.createGame("bataille-corse", 2, GameMode.SOLO);

        assertThat(game, instanceOf(BatailleCorse.class));
    }

    @Test
    void givenBullshitSession_whenRematch_thenStaysABullshit() {
        Game game = service.createGame("bullshit", 3, GameMode.SOLO);

        Game fresh = service.rematch(game.getId());

        assertThat(fresh, instanceOf(Bullshit.class));
    }
}
