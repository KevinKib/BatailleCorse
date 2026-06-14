package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameFactoriesTest {

    private final GameFactories factories = new GameFactories(
            List.of(new BatailleCorseFactory(), new BullshitFactory()));

    @Test
    void givenBullshitSlug_whenFactoryFor_thenCreatesBullshit() {
        assertThat(factories.factoryFor("bullshit").create(GameId.generate(), 3),
                instanceOf(Bullshit.class));
    }

    @Test
    void givenBatailleCorseSlug_whenFactoryFor_thenCreatesBatailleCorse() {
        assertThat(factories.factoryFor("bataille-corse").create(GameId.generate(), 2),
                instanceOf(BatailleCorse.class));
    }

    @Test
    void givenUnknownSlug_whenFactoryFor_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> factories.factoryFor("chess"));
    }
}
