package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario.afterSends;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

class BatailleCorseSlapWouldSucceedTest {

    @Test
    void anEmptyPileIsNotSlappable() {
        assertThat(afterSends(List.of(TWO), List.of(THREE), 0).slapWouldSucceed(), is(false));
    }

    @Test
    void aTenOnTopIsSlappable() {
        assertThat(afterSends(List.of(TEN), List.of(THREE), 1).slapWouldSucceed(), is(true));
    }

    @Test
    void anOrdinaryPileIsNotSlappable() {
        assertThat(afterSends(List.of(TWO, FOUR), List.of(THREE, FIVE), 2).slapWouldSucceed(), is(false));
    }

    @Test
    void askingDoesNotChangeTheGame() {
        BatailleCorse game = afterSends(List.of(TEN), List.of(THREE), 1);
        int version = game.version();

        game.slapWouldSucceed();

        assertThat(game.version(), is(version));
        assertThat(game.getPileSize(), is(1));
    }
}
