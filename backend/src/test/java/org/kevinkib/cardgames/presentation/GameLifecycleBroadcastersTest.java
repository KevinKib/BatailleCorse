package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.GameId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameLifecycleBroadcastersTest {

    private final GameLifecycleBroadcaster bcBroadcaster =
            new BatailleCorseLifecycleBroadcaster(null, null);
    private final GameLifecycleBroadcasters broadcasters =
            new GameLifecycleBroadcasters(List.of(bcBroadcaster));

    @Test
    void givenBatailleCorse_whenResolve_thenReturnsBatailleCorseBroadcaster() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);

        assertThat(broadcasters.broadcasterFor(game), sameInstance(bcBroadcaster));
    }

    @Test
    void givenUnsupportedGame_whenResolve_thenThrows() {
        FakeGame game = new FakeGame(GameId.generate(), 2);

        assertThrows(IllegalStateException.class, () -> broadcasters.broadcasterFor(game));
    }
}
