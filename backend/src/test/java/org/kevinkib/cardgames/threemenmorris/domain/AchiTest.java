package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Achi: four pieces each on the Tapatan board, eight pieces on nine points (one hole). */
class AchiTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private ThreeMenMorris achi() {
        return new ThreeMenMorris(GameId.generate(), MorrisRules.ACHI, WHITE);
    }

    /** Alternate placements starting with White. */
    private void place(ThreeMenMorris game, int... points) throws ThreeMenMorrisException {
        for (int i = 0; i < points.length; i++) {
            game.place(i % 2 == 0 ? WHITE : BLACK, points[i]);
        }
    }

    /** White 0 2 3 7, Black 1 4 5 6, hole 8: no line, movement phase, White to move. */
    private ThreeMenMorris achiWithOneHoleAtEight() throws ThreeMenMorrisException {
        ThreeMenMorris game = achi();
        place(game, 0, 1, 2, 4, 7, 5, 3, 6);
        return game;
    }

    @Test
    void eachPlayerStartsWithFourPiecesInHandAndWhiteStarts() {
        ThreeMenMorris game = achi();

        assertThat(game.piecesToPlace(WHITE), is(4));
        assertThat(game.piecesToPlace(BLACK), is(4));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(game.legalPlacements(WHITE).size(), is(9));
    }

    @Test
    void placementLastsUntilAllEightPiecesAreDownThenMovementStartsWithWhite() throws Exception {
        ThreeMenMorris game = achi();
        place(game, 0, 1, 2, 4, 7, 5, 3);

        assertThat(game.phase(), is(Phase.PLACEMENT));
        assertThat(game.piecesToPlace(BLACK), is(1));

        game.place(BLACK, 6);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(0));
        assertThat(game.piecesToPlace(BLACK), is(0));
        assertThat(game.result().isFinished(), is(false));
    }

    @Test
    void onlyThePiecesNextToTheHoleCanMove() throws Exception {
        ThreeMenMorris game = achiWithOneHoleAtEight();

        assertThat(game.legalMoves(WHITE), is(List.of(new Move(7, 8))));
        assertThat(game.legalMoves(BLACK), is(List.of()));

        game.move(WHITE, 7, 8);

        assertThat(game.legalMoves(BLACK), containsInAnyOrder(new Move(4, 7), new Move(6, 7)));
    }

    @Test
    void movingAPieceNotNextToTheHoleIsRefused() throws Exception {
        ThreeMenMorris game = achiWithOneHoleAtEight();

        assertThrows(PointOccupiedException.class, () -> game.move(WHITE, 0, 1));
        assertThat(game.version(), is(8));
    }

    @Test
    void aLineMadeDuringPlacementWinsAtOnce() throws Exception {
        ThreeMenMorris game = achi();

        place(game, 0, 3, 1, 4, 2);

        assertThat(game.isFinished(), is(true));
        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 1, 2)));
    }

    @Test
    void aLineMadeByAMoveWithFourPiecesWins() throws Exception {
        ThreeMenMorris game = achi();
        // White 0 1 5 6, Black 2 3 4 7, hole 8
        place(game, 0, 2, 1, 3, 5, 4, 6, 7);
        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.legalMoves(WHITE), is(List.of(new Move(5, 8))));

        game.move(WHITE, 5, 8);
        game.move(BLACK, 2, 5);

        assertThat(game.result(), is(new MorrisResult(BLACK, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(3, 4, 5)));
    }

    @Test
    void whiteBlockedRightAfterTheEighthPlacementLosesAtOnce() throws Exception {
        ThreeMenMorris game = achi();
        // hole 0 (neighbours 1, 3, 4, all Black's): White 2 5 6 7, Black 1 3 4 8
        place(game, 2, 1, 5, 3, 6, 4, 7, 8);

        assertThat(game.isFinished(), is(true));
        assertThat(game.result(), is(new MorrisResult(BLACK, MorrisResult.Reason.BLOCKED)));
        assertThat(game.winningLine(), is(List.of()));
    }

    @Test
    void thePointsOfTheBoardAreStillNineWhateverThePieceCount() {
        ThreeMenMorris game = achi();

        assertThrows(InvalidPointException.class, () -> game.place(WHITE, 9));
        assertThat(game.board().size(), is(9));
    }
}
