package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** The immutable snapshot of a game and its key (board and seat to move). */
class MorrisPositionTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private static ThreeMenMorris layout(String cells, PlayerId current) {
        return ThreeMenMorris.fromLayout(GameId.generate(), cells, 0, 0, current);
    }

    @Test
    void thePositionHoldsTheBoardTheHandsTheSeatToMoveAndTheRules() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        game.place(WHITE, 4);

        MorrisPosition position = game.position();

        assertThat(position.rules(), is(MorrisRules.TAPATAN));
        assertThat(position.board().get(4), is(0));
        assertThat(position.board().get(0), is((Integer) null));
        assertThat(position.inHand(), is(List.of(2, 3)));
        assertThat(position.toMove(), is(1));
    }

    @Test
    void theSnapshotDoesNotFollowTheGame() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        MorrisPosition before = game.position();

        game.place(WHITE, 4);

        assertThat(before.board().get(4), is((Integer) null));
        assertThat(before.toMove(), is(0));
    }

    @Test
    void keysAreEqualForEqualBoardsAndSideToMove() {
        MorrisPosition a = layout("WBW.B..WB", WHITE).position();
        MorrisPosition b = layout("WBW.B..WB", WHITE).position();

        assertThat(a.key(), is(b.key()));
        assertThat(a.key().hashCode(), is(b.key().hashCode()));
    }

    @Test
    void removingDefaultsToFalseAndIsPartOfEqualityButNotOfTheKey() {
        MorrisPosition plain = layout("WBW.B..WB", WHITE).position();
        MorrisPosition removing = new MorrisPosition(plain.rules(), plain.board(), plain.inHand(),
                plain.toMove(), true);

        assertThat(plain.removing(), is(false));
        assertThat(removing.removing(), is(true));
        assertThat(removing, is(not(plain)));
        assertThat(removing.key(), is(plain.key()));
        assertThat(removing, is(new MorrisPosition(plain.rules(), plain.board(), plain.inHand(),
                plain.toMove(), true)));
    }

    @Test
    void keysDifferWhenTheBoardOrTheSideToMoveDiffer() {
        MorrisPosition base = layout("WBW.B..WB", WHITE).position();

        assertThat(layout("WBW.B..WB", BLACK).position().key(), is(not(base.key())));
        assertThat(layout("WBWB...WB", WHITE).position().key(), is(not(base.key())));
    }
}
