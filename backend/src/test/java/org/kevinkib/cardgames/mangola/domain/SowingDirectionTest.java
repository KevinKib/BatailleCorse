package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SowingDirectionTest {

    @Test
    void keysAreTheWireNames() {
        assertThat(SowingDirection.ANTICLOCKWISE.key(), is("anticlockwise"));
        assertThat(SowingDirection.CLOCKWISE.key(), is("clockwise"));
    }

    @Test
    void fromKeyReadsBothValues() {
        assertThat(SowingDirection.fromKey("anticlockwise"), is(SowingDirection.ANTICLOCKWISE));
        assertThat(SowingDirection.fromKey("clockwise"), is(SowingDirection.CLOCKWISE));
    }

    @Test
    void fromKeyRefusesAnUnknownKey() {
        assertThrows(IllegalArgumentException.class, () -> SowingDirection.fromKey("sideways"));
    }

    @Test
    void theDefaultIsAnticlockwise() {
        assertThat(SowingDirection.DEFAULT, is(SowingDirection.ANTICLOCKWISE));
    }

    @Test
    void aGameBuiltWithoutADirectionUsesTheDefault() {
        PlayerId seatA = new PlayerId(0);

        Mangola placement = new Mangola(GameId.generate(), MangolaRules.OWNER, seatA);
        Mangola play = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, seatA, MangolaBoard.empty());
        Mangola playToMove = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, seatA,
                MangolaBoard.empty(), new PlayerId(1));

        assertThat(placement.direction(), is(SowingDirection.DEFAULT));
        assertThat(play.direction(), is(SowingDirection.DEFAULT));
        assertThat(playToMove.direction(), is(SowingDirection.DEFAULT));
    }

    @Test
    void theFactoryKeyIsDirection() {
        assertThat(MangolaFactory.DIRECTION_KEY, is("direction"));
    }
}
