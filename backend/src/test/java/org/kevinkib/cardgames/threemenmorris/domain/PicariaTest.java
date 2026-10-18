package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Picaria on the 13-point board: three pieces each, the centre closed during the whole placement phase, four crossings. */
class PicariaTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private ThreeMenMorris picaria() {
        return new ThreeMenMorris(GameId.generate(), MorrisRules.PICARIA, WHITE);
    }

    private void place(ThreeMenMorris game, int... points) throws ThreeMenMorrisException {
        for (int i = 0; i < points.length; i++) {
            game.place(i % 2 == 0 ? WHITE : BLACK, points[i]);
        }
    }

    @Test
    void theCentreIsClosedThroughoutThePlacementPhase() throws Exception {
        ThreeMenMorris game = picaria();

        assertThat(game.legalPlacements(WHITE), is(List.of(0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12)));
        assertThrows(PointLockedException.class, () -> game.place(WHITE, 4));
        assertThat(game.version(), is(0));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(3));

        game.place(WHITE, 1);
        game.place(BLACK, 0);

        // both players have placed one piece: the centre stays closed
        assertThat(game.legalPlacements(WHITE), is(List.of(2, 3, 5, 6, 7, 8, 9, 10, 11, 12)));
        assertThrows(PointLockedException.class, () -> game.place(WHITE, 4));
        place(game, 6, 2, 8);
        assertThat(game.legalPlacements(BLACK), is(List.of(3, 5, 7, 9, 10, 11, 12)));
        assertThrows(PointLockedException.class, () -> game.place(BLACK, 4));
        assertThat(game.version(), is(5));
    }

    @Test
    void theCentreIsReachableByAMoveInTheMovementPhase() throws Exception {
        ThreeMenMorris game = picaria();
        place(game, 1, 0, 6, 2, 8, 5);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.owner(4), is((PlayerId) null));

        game.move(WHITE, 1, 4);

        assertThat(game.owner(4), is(WHITE));
    }

    @Test
    void aLockedCentreIsReportedAsLockedEvenWhenFree() {
        ThreeMenMorris game = picaria();

        PointLockedException error = assertThrows(PointLockedException.class, () -> game.place(WHITE, 4));
        assertThat(error instanceof ThreeMenMorrisException, is(true));
    }

    @Test
    void anOccupiedPointStillReportsOccupiedBeforeTheLock() throws Exception {
        ThreeMenMorris game = picaria();
        place(game, 1);

        assertThrows(PointOccupiedException.class, () -> game.place(BLACK, 1));
    }

    @Test
    void theCrossingsAreLegalMovesAndPlacements() throws Exception {
        ThreeMenMorris game = picaria();
        // White 1 6 8, Black 0 2 5, holes 3 4 7 and the crossings 9 10 11 12
        place(game, 1, 0, 6, 2, 8, 5);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.legalMoves(WHITE), containsInAnyOrder(
                new Move(1, 4), new Move(1, 9), new Move(1, 10),
                new Move(6, 3), new Move(6, 7), new Move(6, 12),
                new Move(8, 7), new Move(8, 11)));

        game.move(WHITE, 1, 9);

        assertThat(game.owner(9), is(WHITE));
        assertThat(game.owner(1), is((PlayerId) null));
    }

    @Test
    void aCornerIsNotAdjacentToTheCentreAndNoJumpIsAllowed() throws Exception {
        ThreeMenMorris game = picaria();
        // White 0 6 8, Black 1 2 5: White to move
        place(game, 0, 1, 6, 2, 8, 5);

        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 4));
        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 12));
        game.move(WHITE, 0, 9);
        assertThat(game.owner(9), is(WHITE));
    }

    @Test
    void aCrossingDoesNotExistInTapatan() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        assertThrows(InvalidPointException.class, () -> game.place(WHITE, 9));
    }

    @Test
    void aLineAlongTheDiamondMadeByPlacementWins() throws Exception {
        ThreeMenMorris game = picaria();

        place(game, 1, 0, 9, 2, 3);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(1, 9, 3)));
    }

    @Test
    void threeInARowOnACornerDiagonalNeedsConsecutivePoints() throws Exception {
        ThreeMenMorris game = picaria();
        // White 9 11 and one more on the diagonal 0-9-4-11-8: 9 and 11 are not consecutive, 4 is between them
        place(game, 9, 0, 11, 2, 8, 3);

        assertThat(game.isFinished(), is(false));
        assertThat(game.phase(), is(Phase.MOVEMENT));
    }

    @Test
    void aMoveIntoTheCentreCanCompleteACornerDiagonalLine() throws Exception {
        ThreeMenMorris game = picaria();
        // White 0 9 and 1, Black 2 5 3: 1 -> 4 makes 0-9-4
        place(game, 0, 2, 9, 5, 1, 3);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        game.move(WHITE, 1, 4);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 9, 4)));
    }

    @Test
    void aLineMadeDuringPlacementStillWinsOffTheCentre() throws Exception {
        ThreeMenMorris game = picaria();

        place(game, 0, 3, 1, 5, 2);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 1, 2)));
    }

    @Test
    void aCrossingMoveCanCompleteADiamondLine() throws Exception {
        ThreeMenMorris game = picaria();
        // White 1 3 0, Black 5 7 8: 0 -> 9 closes the diamond edge 1-9-3
        place(game, 1, 5, 3, 7, 0, 8);

        game.move(WHITE, 0, 9);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(1, 9, 3)));
    }

    @Test
    void theLockDoesNotApplyToTapatan() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        game.place(WHITE, 4);

        assertThat(game.owner(4), is(WHITE));
    }
}
