package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The rules as data: the engine reads the geometry and the piece count, Tapatan is the v1 instance. */
class ThreeMenMorrisRulesTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    @Test
    void tapatanIsNineFreePointsAndThreePiecesEach() {
        assertThat(MorrisRules.TAPATAN.geometry(), is(Geometry.TAPATAN));
        assertThat(MorrisRules.TAPATAN.piecesPerPlayer(), is(3));
    }

    @Test
    void theV1ConstructorIsTapatanWithSeatZeroWhite() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        assertThat(game.rules(), is(MorrisRules.TAPATAN));
        assertThat(game.whiteSeat(), is(WHITE));
        assertThat(game.colourOf(WHITE), is(Colour.WHITE));
        assertThat(game.colourOf(BLACK), is(Colour.BLACK));
        assertThat(game.currentSeat(), is(WHITE));
        assertThat(game.piecesToPlace(WHITE), is(3));
        assertThat(game.piecesToPlace(BLACK), is(3));
    }

    @Test
    void theExplicitConstructorWithTapatanAndSeatZeroIsTheSameGame() {
        ThreeMenMorris v1 = new ThreeMenMorris(GameId.generate());
        ThreeMenMorris explicit = new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, WHITE);

        assertThat(explicit.rules(), is(v1.rules()));
        assertThat(explicit.whiteSeat(), is(v1.whiteSeat()));
        assertThat(explicit.board(), is(v1.board()));
        assertThat(explicit.currentSeat(), is(v1.currentSeat()));
        assertThat(explicit.phase(), is(v1.phase()));
        assertThat(explicit.version(), is(v1.version()));
    }

    @Test
    void theWhiteSeatStartsAndHasTheWhiteColour() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, BLACK);

        assertThat(game.whiteSeat(), is(BLACK));
        assertThat(game.colourOf(BLACK), is(Colour.WHITE));
        assertThat(game.colourOf(WHITE), is(Colour.BLACK));
        assertThat(game.currentSeat(), is(BLACK));
    }

    @Test
    void anUnknownWhiteSeatIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThreeMenMorris(GameId.generate(), MorrisRules.TAPATAN, new PlayerId(2)));
    }

    @Test
    void legalPlacementsAreTheFreePointsForTheSeatToPlayInThePlacementPhase() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        assertThat(game.legalPlacements(WHITE), contains(0, 1, 2, 3, 4, 5, 6, 7, 8));
        assertThat(game.legalPlacements(BLACK), is(empty()));

        game.place(WHITE, 4);
        game.place(BLACK, 0);

        assertThat(game.legalPlacements(WHITE), contains(1, 2, 3, 5, 6, 7, 8));
        assertThat(game.legalPlacements(BLACK), is(empty()));
    }

    @Test
    void thereIsNoLegalPlacementInTheMovementPhaseOrOnceFinished() {
        ThreeMenMorris movement = ThreeMenMorris.fromLayout(GameId.generate(), "WBW.B..WB", 0, 0, WHITE);
        assertThat(movement.phase(), is(Phase.MOVEMENT));
        assertThat(movement.legalPlacements(WHITE), is(empty()));

        ThreeMenMorris finished = new ThreeMenMorris(GameId.generate());
        finished.forfeit(BLACK);
        assertThat(finished.legalPlacements(WHITE), is(empty()));
    }

    @Test
    void thePiecesPerPlayerComeFromTheRules() throws Exception {
        MorrisRules fourPieces = new MorrisRules(Geometry.TAPATAN, 4);
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), fourPieces, WHITE);

        assertThat(game.piecesToPlace(WHITE), is(4));
        // 0 1 2 / 3 4 5 / 6 7 8: no line is made while placing 4 pieces each
        for (int[] placement : new int[][]{{0, 0}, {1, 1}, {0, 5}, {1, 3}, {0, 7}, {1, 2}, {0, 6}}) {
            game.place(new PlayerId(placement[0]), placement[1]);
        }
        assertThat(game.phase(), is(Phase.PLACEMENT));
        game.place(BLACK, 8);

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.piecesToPlace(WHITE), is(0));
    }

    @Test
    void theEngineReadsTheLinesOfTheGeometry() throws Exception {
        Geometry chain = new Geometry(5,
                List.of(List.of(1), List.of(0, 2), List.of(1, 3), List.of(2, 4), List.of(3)),
                List.of(List.of(0, 1, 2)));
        MorrisRules rules = new MorrisRules(chain, 3);
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), rules, WHITE);

        game.place(WHITE, 0);
        game.place(BLACK, 4);
        game.place(WHITE, 1);
        game.place(BLACK, 3);
        game.place(WHITE, 2);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(0, 1, 2)));
        assertThat(game.board().size(), is(5));
    }

    @Test
    void aMoveFollowsTheAdjacencyOfTheGeometry() throws Exception {
        Geometry chain = new Geometry(4,
                List.of(List.of(1), List.of(0, 2), List.of(1, 3), List.of(2)),
                List.of());
        MorrisRules rules = new MorrisRules(chain, 1);
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), rules, WHITE);
        game.place(WHITE, 0);
        game.place(BLACK, 3);

        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 2));
        game.move(WHITE, 0, 1);
        assertThat(game.owner(1), is(WHITE));
        assertThrows(InvalidPointException.class, () -> game.move(BLACK, 3, 4));
    }
}
