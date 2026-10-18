package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.BLACK;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.WHITE;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.points;

/** How a Six Men's Morris game ends: two pieces left, no move, or a forfeit. */
class SixMenMorrisEndTest {

    @Test
    void aPlayerReducedToTwoPiecesDuringPlacementLosesWithThePiecesInHandCounted() throws Exception {
        // Black holds a piece on 8 and two in hand: three in all. Taking 8 leaves two.
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(0, 1), points(8), WHITE, 6, 3);
        game.place(WHITE, 2);
        assertThat(game.piecesLeft(BLACK), is(3));

        game.remove(WHITE, 8);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.REDUCED)));
    }

    @Test
    void aPlayerWithThreePiecesLeftKeepsPlaying() throws Exception {
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(0, 1), points(8, 12), WHITE, 6, 4);
        game.place(WHITE, 2);

        game.remove(WHITE, 8);

        assertThat(game.isFinished(), is(false));
        assertThat(game.piecesLeft(BLACK), is(3));
        assertThat(game.currentSeat(), is(BLACK));
    }

    @Test
    void aSeatCanBeBlockedWithoutAnyMillWhenTheLastPieceIsMoved() throws Exception {
        // White moves 12 -> 11 and walls in black's three pieces 0 2 4 (white on 1 3 5 7); no line is closed.
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(1, 3, 5, 7, 12, 9),
                points(0, 2, 4), WHITE, 6, 3);

        game.move(WHITE, 12, 11);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.BLOCKED)));
    }

    @Test
    void aForfeitStillEndsTheGameAndAFinishedGameRefusesEverything() throws Exception {
        ThreeMenMorris game = SixGames.freshSix();

        game.forfeit(BLACK);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.FORFEIT)));
        org.junit.jupiter.api.Assertions.assertThrows(FinishedGameException.class, () -> game.place(WHITE, 0));
        org.junit.jupiter.api.Assertions.assertThrows(FinishedGameException.class, () -> game.remove(WHITE, 0));
    }
}
