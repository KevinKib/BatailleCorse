package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.BLACK;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.WHITE;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.freshSix;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.points;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.six;

/** Six Men's Morris: placement, the mill that opens a removal, and the way into the movement phase. */
class SixMenMorrisTest {

    private static void place(ThreeMenMorris game, int... points) throws ThreeMenMorrisException {
        for (int i = 0; i < points.length; i++) {
            game.place(game.currentSeat(), points[i]);
        }
    }

    @Test
    void aFreshGameHoldsSixPiecesEachOnSixteenPointsAndWhiteStarts() {
        ThreeMenMorris game = freshSix();

        assertThat(game.board().size(), is(16));
        assertThat(game.piecesToPlace(WHITE), is(6));
        assertThat(game.piecesToPlace(BLACK), is(6));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(game.availableActions(WHITE), is(List.of(Action.PLACE)));
        assertThat(game.legalPlacements(WHITE).size(), is(16));
        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.lost(WHITE), is(0));
        assertThat(game.piecesLeft(BLACK), is(6));
    }

    @Test
    void theCreatingConstructorNoLongerRefusesMillRules() {
        assertThat(freshSix().rules(), is(MorrisRules.SIX_MEN));
    }

    @Test
    void aLineOfThreeDuringPlacementIsAMillNotAWin() throws Exception {
        ThreeMenMorris game = freshSix();

        place(game, 0, 8, 1, 10);
        MorrisEvent event = game.place(WHITE, 2);

        assertThat(game.isFinished(), is(false));
        assertThat(game.winningLine(), is(List.of()));
        assertThat(event, is(new MorrisEvent.Placed(WHITE, 2, List.of(0, 1, 2))));
    }

    @Test
    void aMillKeepsTheTurnAndMakesRemoveTheOnlyAction() throws Exception {
        ThreeMenMorris game = freshSix();
        place(game, 0, 8, 1, 10);
        int versionBefore = game.version();

        game.place(WHITE, 2);

        assertThat(game.version(), is(versionBefore + 1));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.pendingRemoval(), is(WHITE));
        assertThat(game.lastMill(), is(List.of(0, 1, 2)));
        assertThat(game.availableActions(WHITE), is(List.of(Action.REMOVE)));
        assertThat(game.availableActions(BLACK), is(List.of()));
        assertThat(game.legalPlacements(WHITE), is(List.of()));
        assertThat(game.legalRemovals(WHITE), is(List.of(8, 10)));
        assertThat(game.legalRemovals(BLACK), is(List.of()));
        assertThat(game.position().removing(), is(true));
        assertThat(game.position().toMove(), is(0));
    }

    @Test
    void aMillClosedByTheLastPieceInTheMiddleOfTheLineCountsToo() throws Exception {
        ThreeMenMorris game = freshSix();

        place(game, 0, 8, 2, 10);
        game.place(WHITE, 1);

        assertThat(game.lastMill(), is(List.of(0, 1, 2)));
    }

    @Test
    void aJoinIsNeverAMillAndTheInnerSquareHasItsOwn() throws Exception {
        ThreeMenMorris joins = freshSix();
        place(joins, 1, 0, 9, 2, 13);
        assertThat(joins.pendingRemoval(), nullValue());

        ThreeMenMorris inner = freshSix();
        place(inner, 8, 0, 9, 2, 10);
        assertThat(inner.pendingRemoval(), is(WHITE));
        assertThat(inner.lastMill(), is(List.of(8, 9, 10)));
    }

    @Test
    void placingOrMovingWhileARemovalIsPendingIsRefusedAndChangesNothing() throws Exception {
        ThreeMenMorris game = freshSix();
        place(game, 0, 8, 1, 10, 2);
        int version = game.version();

        assertThrows(RemovalPendingException.class, () -> game.place(WHITE, 5));
        assertThrows(RemovalPendingException.class, () -> game.move(WHITE, 0, 7));
        assertThrows(NotPlayersTurnException.class, () -> game.place(BLACK, 5));

        assertThat(game.version(), is(version));
        assertThat(game.owner(5), nullValue());
    }

    @Test
    void aMillWhenTheOpponentHasNoPieceOnTheBoardRemovesNothingAndPassesTheTurn() throws Exception {
        ThreeMenMorris game = six(points(0, 1), points(), WHITE);

        game.place(WHITE, 2);

        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.currentSeat(), is(BLACK));
        assertThat(game.lastMill(), is(List.of()));
    }

    @Test
    void aMillAlreadyOnTheBoardDoesNotCountWhenAnotherPieceIsPlaced() throws Exception {
        ThreeMenMorris game = six(points(0, 1, 2), points(8, 12), WHITE);

        game.place(WHITE, 5);

        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.currentSeat(), is(BLACK));
    }

    @Test
    void placementEndsWhenBothHandsAreEmptyAfterTwelvePlacementsAndWhiteMovesFirst() throws Exception {
        ThreeMenMorris game = freshSix();
        // White 0 3 5 9 11 13, black 1 4 6 8 12 15: none of the eight lines is complete.
        int[] sequence = {0, 1, 3, 4, 5, 6, 9, 8, 11, 12, 13, 15};
        place(game, sequence);

        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(0));
        assertThat(game.piecesToPlace(BLACK), is(0));
    }

    @Test
    void aRemovedPieceDoesNotGiveItsOwnerAnotherPlacementSoPlacementStillEndsAfterTwelve() throws Exception {
        ThreeMenMorris game = freshSix();
        place(game, 0, 8, 1, 10, 2);
        game.remove(WHITE, 8);
        // Black has 4 in hand and 1 on the board; white 3 in hand.
        place(game, 3, 4, 5, 6, 12, 14, 13);

        assertThat(game.lost(BLACK), is(1));
        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(0));
        assertThat(game.piecesToPlace(BLACK), is(0));
    }

    @Test
    void theLastPlacementClosingAMillThenRemovedStartsMovementWithWhite() throws Exception {
        // White has placed everything; black places its last piece and closes the mill 8 9 10.
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(0, 3, 5, 12, 13, 15),
                points(8, 9, 1, 4, 6), BLACK, 6, 6);
        assertThat(game.piecesToPlace(BLACK), is(1));

        game.place(BLACK, 10);
        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(game.pendingRemoval(), is(BLACK));
        game.remove(BLACK, 0);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.pendingRemoval(), nullValue());
    }

    @Test
    void everyAcceptedCommandBumpsTheVersionByOne() throws Exception {
        ThreeMenMorris game = freshSix();

        assertThat(game.version(), is(0));
        place(game, 0, 8, 1, 10, 2);
        assertThat(game.version(), is(5));
        game.remove(WHITE, 8);
        assertThat(game.version(), is(6));
    }

    @Test
    void aMillByAMoveClosesThroughTheDestinationAndOpensARemoval() throws Exception {
        ThreeMenMorris game = six(points(0, 1, 3, 9, 12, 15), points(4, 5, 6, 10, 11, 13), WHITE);

        MorrisEvent event = game.move(WHITE, 3, 2);

        assertThat(event, is(new MorrisEvent.Moved(WHITE, 3, 2, List.of(0, 1, 2))));
        assertThat(game.pendingRemoval(), is(WHITE));
        assertThat(game.legalMoves(WHITE), is(List.of()));
        assertThat(game.availableActions(WHITE), is(List.of(Action.REMOVE)));
        assertThat(game.legalRemovals(WHITE), is(List.of(10, 11, 13)));
    }

    @Test
    void aMoveThatDoesNotCloseALineThroughItsDestinationIsNotAMillEvenWithAMillElsewhere() throws Exception {
        ThreeMenMorris game = six(points(4, 5, 6, 9, 12, 15), points(0, 2, 10, 11, 13, 14), WHITE);

        game.move(WHITE, 9, 8);

        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.currentSeat(), is(BLACK));
    }

    @Test
    void movingOutOfAMillAndBackFormsItAgain() throws Exception {
        ThreeMenMorris game = six(points(0, 1, 2, 11, 13, 15), points(4, 6, 9, 10, 12, 14), WHITE);

        game.move(WHITE, 2, 3);
        game.move(BLACK, 4, 5);
        game.move(WHITE, 3, 2);

        assertThat(game.pendingRemoval(), is(WHITE));
        assertThat(game.lastMill(), is(List.of(0, 1, 2)));
    }

    @Test
    void sixHasNoFlyingAPlayerWithThreePiecesStillMovesOneStep() {
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(0, 5, 10), points(2, 4, 8, 12, 13, 15),
                WHITE, 3, 6);

        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 14));
        assertThat(game.legalMoves(WHITE).contains(new Move(0, 14)), is(false));
    }
}
