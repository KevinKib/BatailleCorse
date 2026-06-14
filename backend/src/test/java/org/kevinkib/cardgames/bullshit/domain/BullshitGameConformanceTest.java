package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class BullshitGameConformanceTest {

    @Test
    void givenBullshit_whenViewedAsGame_thenExposesIdPlayersAndFinished() {
        Bullshit bullshit = new Bullshit(GameId.generate(), 3);
        Game asGame = bullshit;

        assertThat(asGame.getId(), is(bullshit.getId()));
        assertThat(asGame.getPlayerIds(), hasSize(3));
        assertThat(asGame.isFinished(), is(false));
    }

    @Test
    void givenThreePlayers_whenForfeitThroughGame_thenPlayerRemoved() {
        Game game = new Bullshit(GameId.generate(), 3);

        game.forfeit(new PlayerId(0));

        assertThat(game.getPlayerIds(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }
}
