package org.kevinkib.cardgames.bataillecorse.domain;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.game.GameId;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatailleCorseConcedeTest {

    private BatailleCorse newTwoPlayerGame() {
        return new BatailleCorse(GameId.generate(), 2);
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

    @Test
    void givenMoreThanTwoPlayers_whenConcede_thenThrows() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 4);

        assertThrows(UnsupportedOperationException.class, () -> game.concede(new PlayerId(0)));
    }
}
