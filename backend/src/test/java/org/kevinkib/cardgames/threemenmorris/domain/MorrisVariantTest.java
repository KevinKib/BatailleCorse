package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The variants as data: option keys, and rules. */
class MorrisVariantTest {

    @Test
    void eachVariantHasItsOptionKeyAndItsRules() {
        assertThat(MorrisVariant.TAPATAN.key(), is("tapatan"));
        assertThat(MorrisVariant.ACHI.key(), is("achi"));
        assertThat(MorrisVariant.TAPATAN.rules(), is(MorrisRules.TAPATAN));
        assertThat(MorrisVariant.ACHI.rules(), is(MorrisRules.ACHI));
        assertThat(MorrisVariant.DEFAULT, is(MorrisVariant.TAPATAN));
    }

    @Test
    void aKeyIsResolvedAndAnUnknownKeyIsRefused() {
        assertThat(MorrisVariant.fromKey("achi"), is(MorrisVariant.ACHI));
        assertThrows(IllegalArgumentException.class, () -> MorrisVariant.fromKey("picaria"));
        assertThrows(IllegalArgumentException.class, () -> MorrisVariant.fromKey("nine"));
        assertThrows(IllegalArgumentException.class, () -> MorrisVariant.fromKey(null));
    }

    @Test
    void theRulesTellTheirVariant() {
        assertThat(MorrisVariant.of(MorrisRules.ACHI), is(MorrisVariant.ACHI));
        assertThat(MorrisVariant.of(MorrisRules.TAPATAN), is(MorrisVariant.TAPATAN));
    }

    @Test
    void achiHasFourPiecesOnTheTapatanBoard() {
        assertThat(MorrisRules.ACHI.piecesPerPlayer(), is(4));
        assertThat(MorrisRules.ACHI.geometry(), is(Geometry.TAPATAN));
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
