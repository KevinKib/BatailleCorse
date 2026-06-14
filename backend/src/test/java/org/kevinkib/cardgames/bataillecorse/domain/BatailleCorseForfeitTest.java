package org.kevinkib.cardgames.bataillecorse.domain;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.game.GameId;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatailleCorseForfeitTest {

    private BatailleCorse newTwoPlayerGame() {
        return new BatailleCorse(GameId.generate(), 2);
    }

    @Test
    void givenOngoingGame_whenSeatZeroForfeits_thenSeatOneWins() {
        BatailleCorse game = newTwoPlayerGame();

        game.forfeit(new PlayerId(0));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }

    @Test
    void givenAlreadyFinishedGame_whenOtherSeatForfeits_thenWinnerUnchanged() {
        BatailleCorse game = newTwoPlayerGame();
        game.forfeit(new PlayerId(0)); // seat 1 wins

        game.forfeit(new PlayerId(1)); // must be a no-op

        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }

    @Test
    void givenMoreThanTwoPlayers_whenForfeit_thenThrows() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 4);

        assertThrows(UnsupportedOperationException.class, () -> game.forfeit(new PlayerId(0)));
    }

    @Test
    void givenBatailleCorse_whenViewedAsGame_thenExposesIdPlayersAndFinished() {
        BatailleCorse game = newTwoPlayerGame();
        Game asGame = game;

        assertThat(asGame.getId(), is(game.getId()));
        assertThat(asGame.getPlayerIds(), is(game.getPlayers().stream().map(Player::id).toList()));
        assertThat(asGame.isFinished(), is(false));
    }
}
