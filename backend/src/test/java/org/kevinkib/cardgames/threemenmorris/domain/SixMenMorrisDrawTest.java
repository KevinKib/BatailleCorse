package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.BLACK;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.WHITE;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.points;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.six;

/** The draws of Six Men's Morris: the quiet-move limit, repetition and draw offers around a removal. */
class SixMenMorrisDrawTest {

    @Test
    void sixHasAFiftyPlyQuietLimit() {
        assertThat(MorrisRules.SIX_MEN.quietPlyLimit(), is(50));
    }

    @Test
    void fiftyMovementPliesWithoutARemovalDrawTheGame() throws Exception {
        ThreeMenMorris game = SixGames.twoLonePieces(MorrisRules.SIX_MEN);

        SixGames.Walker walker = new SixGames.Walker(game);
        walker.play(49);
        assertThat(game.isFinished(), is(false));
        assertThat(game.quietPlies(), is(49));

        walker.play(1);

        assertThat(game.result(), is(new MorrisResult(null, MorrisResult.Reason.DRAW_MOVE_LIMIT)));
    }

    @Test
    void theQuietPlyCountIsZeroDuringPlacementAndCountsMovementPliesFromTheFirstMove() throws Exception {
        ThreeMenMorris game = SixGames.freshSix();
        assertThat(game.quietPlies(), is(0));

        ThreeMenMorris moving = SixGames.twoLonePieces(MorrisRules.SIX_MEN);
        assertThat(moving.quietPlies(), is(0));
        moving.move(WHITE, 0, 1);
        assertThat(moving.quietPlies(), is(1));
    }

    @Test
    void aRemovalResetsTheQuietPlyCountAndTheMillMoveItselfNeverDraws() throws Exception {
        MorrisRules limitThree = SixGames.sixWithQuietLimit(3);
        ThreeMenMorris game = SixGames.game(limitThree, points(0, 1, 3, 5), points(9, 11, 13, 15), WHITE, 4, 4);

        game.move(WHITE, 5, 4);
        game.move(BLACK, 9, 8);
        game.move(WHITE, 3, 2); // third ply, closes 0 1 2: the removal comes first, no draw
        assertThat(game.isFinished(), is(false));
        assertThat(game.pendingRemoval(), is(WHITE));

        game.remove(WHITE, 15);
        assertThat(game.quietPlies(), is(0));
        assertThat(game.isFinished(), is(false));

        game.move(BLACK, 8, 9);
        game.move(WHITE, 4, 5);
        assertThat(game.isFinished(), is(false));
        game.move(BLACK, 9, 8);

        assertThat(game.result(), is(new MorrisResult(null, MorrisResult.Reason.DRAW_MOVE_LIMIT)));
        assertThat(game.result().isDraw(), is(true));
    }

    @Test
    void aThreefoldRepetitionStillDrawsOnSix() throws Exception {
        ThreeMenMorris game = SixGames.twoLonePieces(MorrisRules.SIX_MEN);

        for (int round = 0; round < 2; round++) {
            game.move(WHITE, 0, 1);
            game.move(BLACK, 8, 9);
            game.move(WHITE, 1, 0);
            game.move(BLACK, 9, 8);
        }

        assertThat(game.result(), is(new MorrisResult(null, MorrisResult.Reason.DRAW_REPETITION)));
    }

    @Test
    void aRepetitionIsNotCountedWhileARemovalIsPendingAndTheCountStartsAgainAfterIt() throws Exception {
        ThreeMenMorris game = six(points(0, 1, 3, 9, 12, 15), points(4, 5, 6, 10, 11, 13), WHITE);
        assertThat(game.repetitions(), is(1));

        game.move(WHITE, 3, 2);
        assertThat(game.repetitions(), is(0));

        game.remove(WHITE, 10);
        assertThat(game.repetitions(), is(1));
    }

    @Test
    void aRemovalCancelsAPendingDrawOffer() throws Exception {
        ThreeMenMorris game = six(points(0, 1, 3, 9, 12, 15), points(4, 5, 6, 10, 11, 13), WHITE);
        game.move(WHITE, 3, 2);
        game.offerDraw(BLACK);
        assertThat(game.drawOffer(), is(BLACK));
        int version = game.version();

        game.remove(WHITE, 10);

        assertThat(game.drawOffer(), nullValue());
        assertThat(game.version(), is(version + 1));
    }

    @Test
    void aDrawCanBeOfferedAndAcceptedWhileARemovalIsPending() throws Exception {
        ThreeMenMorris game = six(points(0, 1, 3, 9, 12, 15), points(4, 5, 6, 10, 11, 13), WHITE);
        game.move(WHITE, 3, 2);

        game.offerDraw(BLACK);
        game.acceptDraw(WHITE);

        assertThat(game.result(), is(new MorrisResult(null, MorrisResult.Reason.DRAW_AGREED)));
    }
}
