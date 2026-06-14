package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class BatailleCorseFactoryTest {

    @Test
    void createsAPlayableBatailleCorseWithTheGivenId() {
        GameId id = GameId.generate();

        Game game = new BatailleCorseFactory().create(id, 2);

        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }
}
