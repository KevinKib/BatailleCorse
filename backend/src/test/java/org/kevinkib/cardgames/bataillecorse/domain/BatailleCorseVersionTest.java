package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario.game;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

class BatailleCorseVersionTest {

    @Test
    void aNewGameStartsAtVersionZero() {
        assertThat(game(List.of(TWO, THREE), List.of(FOUR, FIVE)).version(), is(0));
    }

    @Test
    void sendBumpsTheVersion() throws Exception {
        BatailleCorse game = game(List.of(TWO, THREE), List.of(FOUR, FIVE));

        game.send(game.getPlayerByIndex(0));

        assertThat(game.version(), is(1));
    }

    @Test
    void aRefusedSendLeavesTheVersionUnchanged() {
        BatailleCorse game = game(List.of(TWO, THREE), List.of(FOUR, FIVE));

        assertThrows(NotPlayersTurnException.class, () -> game.send(game.getPlayerByIndex(1)));

        assertThat(game.version(), is(0));
    }

    @Test
    void aSuccessfulSlapBumpsTheVersion() throws Exception {
        BatailleCorse game = game(List.of(TEN, THREE), List.of(FOUR, FIVE));
        game.send(game.getPlayerByIndex(0));

        boolean successful = game.slap(game.getPlayerByIndex(1));

        assertThat(successful, is(true));
        assertThat(game.version(), is(2));
    }

    @Test
    void aFailedSlapBumpsTheVersionToo() throws Exception {
        BatailleCorse game = game(List.of(TWO, THREE, FOUR), List.of(FIVE, SIX, SEVEN));
        game.send(game.getPlayerByIndex(0));

        boolean successful = game.slap(game.getPlayerByIndex(1));

        assertThat(successful, is(false));
        assertThat(game.version(), is(2));
    }

    @Test
    void aGrabBumpsTheVersion() throws Exception {
        BatailleCorse game = game(List.of(JACK, THREE, FOUR), List.of(FIVE, SIX, SEVEN));
        game.send(game.getPlayerByIndex(0));
        game.send(game.getPlayerByIndex(1));

        game.grab(game.getPlayerByIndex(0));

        assertThat(game.version(), is(3));
    }

    @Test
    void aRefusedGrabLeavesTheVersionUnchanged() throws Exception {
        BatailleCorse game = game(List.of(TWO, THREE), List.of(FOUR, FIVE));
        game.send(game.getPlayerByIndex(0));

        assertThrows(CannotGrabException.class, () -> game.grab(game.getPlayerByIndex(0)));

        assertThat(game.version(), is(1));
    }

    @Test
    void aForfeitBumpsTheVersionOnceAndIsIdempotent() {
        BatailleCorse game = game(List.of(TWO, THREE), List.of(FOUR, FIVE));

        game.forfeit(new PlayerId(0));
        game.forfeit(new PlayerId(1));

        assertThat(game.version(), is(1));
    }
}
