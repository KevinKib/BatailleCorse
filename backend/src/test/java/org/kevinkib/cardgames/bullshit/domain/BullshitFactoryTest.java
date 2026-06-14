package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BullshitFactoryTest {

    @Test
    void givenId_whenCreate_thenReturnsPlayableBullshitWithThatId() {
        GameId id = GameId.generate();

        Game game = new BullshitFactory().create(id, 3);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(3));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void gameType_isBullshit() {
        assertThat(new BullshitFactory().gameType(), is("bullshit"));
    }
}
