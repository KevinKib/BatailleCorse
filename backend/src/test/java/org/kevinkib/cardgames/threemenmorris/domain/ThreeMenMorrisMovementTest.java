package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Boards are written row by row, nine characters: W white, B black, . empty. */
class ThreeMenMorrisMovementTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private static ThreeMenMorris movement(String layout, PlayerId current) {
        return ThreeMenMorris.fromLayout(GameId.generate(), layout, 0, 0, current);
    }

    @Test
    void movingAPieceToAnAdjacentFreePointPassesTheTurn() throws Exception {
        ThreeMenMorris game = movement("W.B" + ".WB" + "W.B", WHITE);

        MorrisEvent event = game.move(WHITE, 0, 1);

        assertThat(event, is(new MorrisEvent.Moved(WHITE, 0, 1)));
        assertThat(game.owner(0), is(nullValue()));
        assertThat(game.owner(1), is(WHITE));
        assertThat(game.currentSeat(), is(BLACK));
        assertThat(game.version(), is(1));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void aRejectedMoveNeverMutatesTheGame() {
        ThreeMenMorris game = movement("W.B" + ".WB" + "W.B", WHITE);

        assertThrows(NotPlayersTurnException.class, () -> game.move(BLACK, 2, 1));
        assertThrows(NotYourPieceException.class, () -> game.move(WHITE, 2, 1));
        assertThrows(NotYourPieceException.class, () -> game.move(WHITE, 7, 6));
        assertThrows(PointOccupiedException.class, () -> game.move(WHITE, 0, 4));
        assertThrows(PointOccupiedException.class, () -> game.move(WHITE, 4, 5));
        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 7));
        assertThrows(InvalidPointException.class, () -> game.move(WHITE, 4, 9));
        assertThrows(InvalidPointException.class, () -> game.move(WHITE, -1, 1));
        assertThrows(WrongPhaseException.class, () -> game.place(WHITE, 1));

        assertThat(game.version(), is(0));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.owner(0), is(WHITE));
    }

    @Test
    void thereIsNoJumpOverAPiece() {
        ThreeMenMorris game = movement("WB." + "B.B" + "W.W", WHITE);

        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 2));
    }

    @Test
    void aCornerStepsToTheCentreAlongTheDiagonal() throws Exception {
        ThreeMenMorris game = movement("W.W" + "..B" + "BWB", WHITE);

        game.move(WHITE, 0, 4);

        assertThat(game.owner(4), is(WHITE));
    }

    @Test
    void aSideMiddleStepsToTheCentre() throws Exception {
        ThreeMenMorris game = movement(".WB" + "W.B" + "B.W", WHITE);

        game.move(WHITE, 1, 4);

        assertThat(game.owner(4), is(WHITE));
    }

    @Test
    void legalMovesListsEveryFreeAdjacentPointOfTheSeatToPlayAndNothingForTheOther() {
        ThreeMenMorris game = movement("W.W" + "..B" + "BWB", WHITE);

        assertThat(game.legalMoves(WHITE), containsInAnyOrder(new Move(0, 1), new Move(0, 3), new Move(0, 4),
                new Move(2, 1), new Move(2, 4), new Move(7, 4)));
        assertThat(game.legalMoves(BLACK), is(empty()));
        assertThat(game.availableActions(WHITE), is(List.of(Action.MOVE)));
        assertThat(game.availableActions(BLACK), is(List.of()));
    }

    @Test
    void movingIntoARowWins() throws Exception {
        ThreeMenMorris game = movement("WW." + "B.W" + "B.B", WHITE);

        game.move(WHITE, 5, 2);

        assertThat(game.isFinished(), is(true));
        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 1, 2)));
        assertThat(game.availableActions(BLACK), is(List.of()));
        assertThrows(FinishedGameException.class, () -> game.move(BLACK, 6, 7));
    }

    @Test
    void movingIntoADiagonalWins() throws Exception {
        ThreeMenMorris game = movement("W.B" + "B.W" + "B.W", BLACK);

        game.move(BLACK, 3, 4);

        assertThat(game.result(), is(new MorrisResult(BLACK, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(2, 4, 6)));
    }

    @Test
    void aPlayerLeftWithoutAnyLegalMoveLosesAndTheMoverWins() throws Exception {
        // Artificial position (white holds four pieces): black's two pieces on 0 and 1 are boxed in by
        // white 2, 3 and 4. Any white move that keeps the box leaves black without a move and without
        // a white line. With three pieces each this cannot happen without a white line first.
        ThreeMenMorris game = ThreeMenMorris.fromLayout(GameId.generate(), "BBW" + "WW." + "..W", 0, 0, 4, 2, WHITE);

        game.move(WHITE, 8, 7);

        assertThat(game.isFinished(), is(true));
        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.BLOCKED)));
        assertThat(game.winningLine(), is(List.of()));
    }
}
