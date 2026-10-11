package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario.afterSends;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

class BatailleCorseAutoGrabTest {

    @Test
    void nobodyGrabsAnEmptyPile() {
        assertThat(afterSends(List.of(TWO), List.of(THREE), 0).autoGrabPlayer(), is(Optional.empty()));
    }

    @Test
    void nobodyGrabsWhileTheHonourCardStillHasChances() {
        // A queen leaves two chances; one card after it, the pile is not full yet.
        assertThat(afterSends(List.of(QUEEN, FOUR), List.of(THREE, FIVE), 2).autoGrabPlayer(),
                is(Optional.empty()));
    }

    @Test
    void theOwnerOfTheHonourCardGrabsOnceTheChancesAreSpent() {
        BatailleCorse game = afterSends(List.of(JACK, FOUR), List.of(THREE, FIVE), 2);

        assertThat(game.autoGrabPlayer().map(Player::id), is(Optional.of(new PlayerId(0))));
    }

    @Test
    void theLastHonourCardWins() {
        // Seat 0 plays a jack, seat 1 answers with a queen; the queen leaves two chances.
        BatailleCorse game = afterSends(List.of(JACK, FOUR, SIX), List.of(QUEEN, FIVE, SEVEN), 4);

        assertThat(game.autoGrabPlayer().map(Player::id), is(Optional.of(new PlayerId(1))));
    }

    @Test
    void nobodyGrabsAfterThePileWasCollected() throws Exception {
        BatailleCorse game = afterSends(List.of(JACK, FOUR), List.of(THREE, FIVE), 2);

        game.grab(game.autoGrabPlayer().orElseThrow());

        assertThat(game.autoGrabPlayer(), is(Optional.empty()));
    }

    @Test
    void nobodyGrabsOnAFinishedGame() {
        BatailleCorse game = afterSends(List.of(JACK, FOUR), List.of(THREE, FIVE), 2);

        game.forfeit(new PlayerId(1));

        assertThat(game.autoGrabPlayer(), is(Optional.empty()));
    }
}
