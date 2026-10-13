package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

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
        assertThat(MorrisRules.PICARIA.placement(), is(MorrisRules.PlacementRule.CENTRE_AFTER_FIRST_PIECES));
    }

    @Test
    void picariaAddsTheFourShortDiagonalsAndKeepsTheEightLines() {
        assertThat(Geometry.PICARIA.points(), is(9));
        assertThat(Geometry.PICARIA.lines(), is(Geometry.TAPATAN.lines()));
        assertThat(Geometry.PICARIA.neighbours(1), containsInAnyOrder(0, 2, 4, 3, 5));
        assertThat(Geometry.PICARIA.neighbours(3), containsInAnyOrder(0, 6, 4, 1, 7));
        assertThat(Geometry.PICARIA.neighbours(5), containsInAnyOrder(2, 8, 4, 1, 7));
        assertThat(Geometry.PICARIA.neighbours(7), containsInAnyOrder(6, 8, 4, 3, 5));
        assertThat(Geometry.PICARIA.neighbours(0), containsInAnyOrder(1, 3, 4));
        assertThat(Geometry.PICARIA.neighbours(4), containsInAnyOrder(0, 1, 2, 3, 5, 6, 7, 8));
        assertThat(Geometry.PICARIA.areAdjacent(1, 3), is(true));
        assertThat(Geometry.PICARIA.areAdjacent(5, 7), is(true));
        assertThat(Geometry.TAPATAN.areAdjacent(1, 3), is(false));
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
