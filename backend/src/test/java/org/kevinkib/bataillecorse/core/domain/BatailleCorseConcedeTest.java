package org.kevinkib.bataillecorse.core.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BatailleCorseConcedeTest {

    private BatailleCorse newTwoPlayerGame() {
        return new BatailleCorse(BatailleCorseId.generate(), 2);
    }

    @Test
    void givenOngoingGame_whenSeatZeroConcedes_thenSeatOneWins() {
        BatailleCorse game = newTwoPlayerGame();

        game.concede(new PlayerId(0));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }

    @Test
    void givenAlreadyFinishedGame_whenOtherSeatConcedes_thenWinnerUnchanged() {
        BatailleCorse game = newTwoPlayerGame();
        game.concede(new PlayerId(0)); // seat 1 wins

        game.concede(new PlayerId(1)); // must be a no-op

        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }
}
