package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Picaria: three pieces each, the centre closed until each player has placed one piece, short diagonals. */
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

        assertThat(game.legalPlacements(WHITE), is(List.of(0, 1, 2, 3, 5, 6, 7, 8)));
        assertThrows(PointLockedException.class, () -> game.place(WHITE, 4));
        assertThat(game.version(), is(0));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(3));

        game.place(WHITE, 1);
        game.place(BLACK, 0);

        // both players have placed one piece: the centre stays closed
        assertThat(game.legalPlacements(WHITE), is(List.of(2, 3, 5, 6, 7, 8)));
        assertThrows(PointLockedException.class, () -> game.place(WHITE, 4));
        place(game, 6, 2, 8);
        assertThat(game.legalPlacements(BLACK), is(List.of(3, 5, 7)));
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
    void theShortDiagonalsAreLegalMoves() throws Exception {
        ThreeMenMorris game = picaria();
        // White 1 6 8, Black 0 2 5, holes 3 4 7
        place(game, 1, 0, 6, 2, 8, 5);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.legalMoves(WHITE), containsInAnyOrder(
                new Move(1, 3), new Move(1, 4), new Move(6, 3), new Move(6, 4), new Move(6, 7), new Move(8, 4),
                new Move(8, 7)));

        game.move(WHITE, 1, 3);

        assertThat(game.owner(3), is(WHITE));
        assertThat(game.owner(1), is((PlayerId) null));
    }

    @Test
    void theSameDiagonalMoveIsRefusedInTapatan() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        place(game, 1, 0, 6, 2, 8, 5);

        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 1, 3));
    }

    @Test
    void aShortDiagonalMakesNoLine() throws Exception {
        ThreeMenMorris game = picaria();
        // White 1 3 7 chain along two short diagonals, Black 0 2 8
        place(game, 1, 0, 3, 2, 7, 8);

        assertThat(game.isFinished(), is(false));
        assertThat(game.phase(), is(Phase.MOVEMENT));
    }

    @Test
    void aLineMadeDuringPlacementStillWinsOffTheCentre() throws Exception {
        ThreeMenMorris game = picaria();

        place(game, 0, 3, 1, 5, 2);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 1, 2)));
    }

    @Test
    void aShortDiagonalMoveCanCompleteALine() throws Exception {
        ThreeMenMorris game = picaria();
        // White 0 6 1, Black 2 8 7, holes 3 4 5: 1 -> 3 closes the column 0 3 6
        place(game, 0, 2, 6, 8, 1, 7);

        game.move(WHITE, 1, 3);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 3, 6)));
    }

    @Test
    void theLockDoesNotApplyToTapatan() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        game.place(WHITE, 4);

        assertThat(game.owner(4), is(WHITE));
    }
}
