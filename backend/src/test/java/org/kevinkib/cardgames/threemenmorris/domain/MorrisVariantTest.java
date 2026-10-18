package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The variants as data: option keys, rules and the short diagonals of Picaria. */
class MorrisVariantTest {

    @Test
    void eachVariantHasItsOptionKeyAndItsRules() {
        assertThat(MorrisVariant.TAPATAN.key(), is("tapatan"));
        assertThat(MorrisVariant.ACHI.key(), is("achi"));
        assertThat(MorrisVariant.PICARIA.key(), is("picaria"));
        assertThat(MorrisVariant.TAPATAN.rules(), is(MorrisRules.TAPATAN));
        assertThat(MorrisVariant.ACHI.rules(), is(MorrisRules.ACHI));
        assertThat(MorrisVariant.PICARIA.rules(), is(MorrisRules.PICARIA));
        assertThat(MorrisVariant.DEFAULT, is(MorrisVariant.TAPATAN));
    }

    @Test
    void aKeyIsResolvedAndAnUnknownKeyIsRefused() {
        assertThat(MorrisVariant.fromKey("picaria"), is(MorrisVariant.PICARIA));
        assertThrows(IllegalArgumentException.class, () -> MorrisVariant.fromKey("nine"));
        assertThrows(IllegalArgumentException.class, () -> MorrisVariant.fromKey(null));
    }

    @Test
    void theRulesTellTheirVariant() {
        assertThat(MorrisVariant.of(MorrisRules.ACHI), is(MorrisVariant.ACHI));
        assertThat(MorrisVariant.of(MorrisRules.PICARIA), is(MorrisVariant.PICARIA));
        assertThat(MorrisVariant.of(MorrisRules.TAPATAN), is(MorrisVariant.TAPATAN));
    }

    @Test
    void achiHasFourPiecesOnTheTapatanBoardAndPicariaThreeWithTheCentreLock() {
        assertThat(MorrisRules.ACHI.piecesPerPlayer(), is(4));
        assertThat(MorrisRules.ACHI.geometry(), is(Geometry.TAPATAN));
        assertThat(MorrisRules.ACHI.placement(), is(MorrisRules.PlacementRule.FREE));
        assertThat(MorrisRules.PICARIA.piecesPerPlayer(), is(3));
        assertThat(MorrisRules.PICARIA.geometry(), is(Geometry.PICARIA));
        assertThat(MorrisRules.PICARIA.placement(), is(MorrisRules.PlacementRule.CENTRE_FORBIDDEN_DURING_PLACEMENT));
    }

    @Test
    void picariaHasThirteenPointsTheGridAndFourCrossingsOnTheDiamond() {
        // Grid 0..8 row by row; crossings of the diamond with the corner diagonals:
        // 9 between 0 and 4, 10 between 2 and 4, 11 between 8 and 4, 12 between 6 and 4.
        assertThat(Geometry.PICARIA.points(), is(13));
        assertThat(Geometry.PICARIA.neighbours(0), containsInAnyOrder(1, 3, 9));
        assertThat(Geometry.PICARIA.neighbours(1), containsInAnyOrder(0, 2, 4, 9, 10));
        assertThat(Geometry.PICARIA.neighbours(2), containsInAnyOrder(1, 5, 10));
        assertThat(Geometry.PICARIA.neighbours(3), containsInAnyOrder(0, 4, 6, 9, 12));
        assertThat(Geometry.PICARIA.neighbours(4), containsInAnyOrder(1, 3, 5, 7, 9, 10, 11, 12));
        assertThat(Geometry.PICARIA.neighbours(5), containsInAnyOrder(2, 4, 8, 10, 11));
        assertThat(Geometry.PICARIA.neighbours(6), containsInAnyOrder(3, 7, 12));
        assertThat(Geometry.PICARIA.neighbours(7), containsInAnyOrder(4, 6, 8, 11, 12));
        assertThat(Geometry.PICARIA.neighbours(8), containsInAnyOrder(5, 7, 11));
        assertThat(Geometry.PICARIA.neighbours(9), containsInAnyOrder(0, 1, 3, 4));
        assertThat(Geometry.PICARIA.neighbours(10), containsInAnyOrder(1, 2, 4, 5));
        assertThat(Geometry.PICARIA.neighbours(11), containsInAnyOrder(4, 5, 7, 8));
        assertThat(Geometry.PICARIA.neighbours(12), containsInAnyOrder(3, 4, 6, 7));
        // a corner is no longer next to the centre: a crossing sits between them
        assertThat(Geometry.PICARIA.areAdjacent(0, 4), is(false));
        assertThat(Geometry.PICARIA.areAdjacent(1, 3), is(false));
        assertThat(Geometry.TAPATAN.areAdjacent(0, 4), is(true));
    }

    @Test
    void picariaHasSixteenLinesTheGridAndEveryTripletAlongADrawnLine() {
        assertThat(Geometry.PICARIA.lines().size(), is(16));
        assertThat(Geometry.PICARIA.lines(), containsInAnyOrder(
                // 3 rows and 3 columns of the grid
                List.of(0, 1, 2), List.of(3, 4, 5), List.of(6, 7, 8),
                List.of(0, 3, 6), List.of(1, 4, 7), List.of(2, 5, 8),
                // corner diagonals 0-9-4-11-8 and 2-10-4-12-6: three consecutive points each
                List.of(0, 9, 4), List.of(9, 4, 11), List.of(4, 11, 8),
                List.of(2, 10, 4), List.of(10, 4, 12), List.of(4, 12, 6),
                // diamond edges
                List.of(1, 9, 3), List.of(1, 10, 5), List.of(5, 11, 7), List.of(7, 12, 3)));
    }

    @Test
    void everyLineIsAChainOfAdjacentPoints() {
        for (List<Integer> line : Geometry.PICARIA.lines()) {
            assertThat(line.toString(), Geometry.PICARIA.areAdjacent(line.get(0), line.get(1))
                    && Geometry.PICARIA.areAdjacent(line.get(1), line.get(2)), is(true));
        }
    }

    @Test
    void movesAndEventsOnlyRefuseNegativePointsTheGeometryDecidesTheUpperBound() {
        PlayerId white = new PlayerId(0);

        assertThat(new Move(0, 15).to(), is(15));
        assertThat(new MorrisEvent.Placed(white, 23).point(), is(23));
        assertThat(new MorrisEvent.Moved(white, 3, 12).to(), is(12));
        assertThrows(IllegalArgumentException.class, () -> new Move(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Move(2, -1));
        assertThrows(IllegalArgumentException.class, () -> new MorrisEvent.Placed(white, -1));
        assertThrows(IllegalArgumentException.class, () -> new MorrisEvent.Moved(white, -1, 0));
    }
}
