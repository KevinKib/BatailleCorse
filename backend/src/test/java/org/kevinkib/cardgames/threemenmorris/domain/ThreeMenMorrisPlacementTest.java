package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreeMenMorrisPlacementTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

    @Test
    void aNewGameIsInPlacementWithWhiteToPlayAndThreePiecesInHand() {
        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(3));
        assertThat(game.piecesToPlace(BLACK), is(3));
        assertThat(game.isFinished(), is(false));
        assertThat(game.version(), is(0));
        assertThat(game.getPlayerIds(), is(List.of(WHITE, BLACK)));
        for (int point = 0; point < 9; point++) {
            assertThat(game.owner(point), is(nullValue()));
        }
    }

    @Test
    void placingPutsThePieceOnThePointPassesTheTurnAndBumpsTheVersion() throws Exception {
        MorrisEvent event = game.place(WHITE, 4);

        assertThat(event, is(new MorrisEvent.Placed(WHITE, 4)));
        assertThat(game.owner(4), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(2));
        assertThat(game.currentSeat(), is(BLACK));
        assertThat(game.version(), is(1));
    }

    @Test
    void aRejectedCommandNeverMutatesTheGameNorBumpsTheVersion() throws Exception {
        game.place(WHITE, 4);

        assertThrows(NotPlayersTurnException.class, () -> game.place(WHITE, 0));
        assertThrows(PointOccupiedException.class, () -> game.place(BLACK, 4));
        assertThrows(InvalidPointException.class, () -> game.place(BLACK, 9));
        assertThrows(InvalidPointException.class, () -> game.place(BLACK, -1));
        assertThrows(WrongPhaseException.class, () -> game.move(BLACK, 0, 1));

        assertThat(game.version(), is(1));
        assertThat(game.currentSeat(), is(BLACK));
        assertThat(game.piecesToPlace(BLACK), is(3));
    }

    @Test
    void anUnknownSeatCannotPlay() {
        assertThrows(NotPlayersTurnException.class, () -> game.place(new PlayerId(2), 0));
    }

    @Test
    void aLineMadeDuringPlacementWinsImmediately() throws Exception {
        game.place(WHITE, 0);
        game.place(BLACK, 3);
        game.place(WHITE, 1);
        game.place(BLACK, 4);
        MorrisEvent last = game.place(WHITE, 2);

        assertThat(last, is(new MorrisEvent.Placed(WHITE, 2)));
        assertThat(game.isFinished(), is(true));
        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 1, 2)));
        assertThrows(FinishedGameException.class, () -> game.place(BLACK, 5));
    }

    @Test
    void aDiagonalLineMadeDuringPlacementWins() throws Exception {
        game.place(WHITE, 0);
        game.place(BLACK, 1);
        game.place(WHITE, 4);
        game.place(BLACK, 2);
        game.place(WHITE, 8);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 4, 8)));
    }

    @Test
    void blackCanWinTheLineToo() throws Exception {
        game.place(WHITE, 0);
        game.place(BLACK, 2);
        game.place(WHITE, 1);
        game.place(BLACK, 4);
        game.place(WHITE, 8);
        game.place(BLACK, 6);

        assertThat(game.result(), is(new MorrisResult(BLACK, MorrisResult.Reason.LINE)));
    }

    @Test
    void afterSixPlacementsWithoutALineTheMovementPhaseStartsWithWhite() throws Exception {
        game.place(WHITE, 0);
        game.place(BLACK, 1);
        game.place(WHITE, 2);
        game.place(BLACK, 4);
        game.place(WHITE, 7);
        game.place(BLACK, 8);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.isFinished(), is(false));
        assertThrows(WrongPhaseException.class, () -> game.place(WHITE, 3));
    }

    @Test
    void availableActionsFollowThePhaseAndTheTurn() throws Exception {
        assertThat(game.availableActions(WHITE), is(List.of(Action.PLACE)));
        assertThat(game.availableActions(BLACK), is(List.of()));
        game.place(WHITE, 0);
        assertThat(game.availableActions(WHITE), is(List.of()));
        assertThat(game.availableActions(BLACK), is(List.of(Action.PLACE)));
    }
}
