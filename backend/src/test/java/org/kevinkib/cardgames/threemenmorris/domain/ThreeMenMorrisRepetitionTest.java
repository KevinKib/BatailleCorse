package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Threefold repetition of movement-phase positions. */
class ThreeMenMorrisRepetitionTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    /** Movement phase, white to move: white 0, 2, 7 and black 1, 5, 8, no line, nobody blocked. */
    private ThreeMenMorris movement() {
        return ThreeMenMorris.fromLayout(GameId.generate(), "WBW..B.WB", 0, 0, WHITE);
    }

    /** A four-move cycle that brings the position back to where it started: 7-6, 8-4, 6-7, 4-8. */
    private void cycle(ThreeMenMorris game) throws Exception {
        game.move(WHITE, 7, 6);
        game.move(BLACK, 8, 4);
        game.move(WHITE, 6, 7);
        game.move(BLACK, 4, 8);
    }

    @Test
    void placementNeverCountsAndTheFirstMovementPositionCountsOnce() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        assertThat(game.repetitions(), is(0));
        int[][] placements = {{0, 0}, {1, 1}, {0, 2}, {1, 5}, {0, 7}, {1, 8}};
        for (int[] p : placements) {
            game.place(new PlayerId(p[0]), p[1]);
            if (game.phase() == Phase.PLACEMENT) {
                assertThat(game.repetitions(), is(0));
            }
        }

        assertThat(game.phase(), is(Phase.MOVEMENT));
        assertThat(game.repetitions(), is(1));
    }

    @Test
    void aPositionSeenTwiceIsReportedAndTheGameGoesOn() throws Exception {
        ThreeMenMorris game = movement();
        assertThat(game.repetitions(), is(1));

        cycle(game);

        assertThat(game.repetitions(), is(2));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void theThirdOccurrenceDrawsWithNoWinner() throws Exception {
        ThreeMenMorris game = movement();
        cycle(game);
        game.move(WHITE, 7, 6);
        game.move(BLACK, 8, 4);
        game.move(WHITE, 6, 7);
        int version = game.version();

        game.move(BLACK, 4, 8);

        assertThat(game.isFinished(), is(true));
        assertThat(game.result(), is(new MorrisResult(null, MorrisResult.Reason.DRAW_REPETITION)));
        assertThat(game.result().isDraw(), is(true));
        assertThat(game.version(), is(version + 1));
        assertThat(game.winningLine(), is(List.of()));
        assertThat(game.repetitions(), is(0));
    }

    @Test
    void theSameBoardWithTheOtherSeatToMoveIsAnotherPosition() throws Exception {
        ThreeMenMorris game = movement();
        game.move(WHITE, 7, 6);
        game.move(BLACK, 8, 4);
        game.move(WHITE, 6, 7);
        game.move(BLACK, 4, 8);
        game.move(WHITE, 7, 6);

        // the board after white's first move was seen once, with black to move: now it is the second time
        assertThat(game.repetitions(), is(2));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void aLineMadeByTheMoveWinsInsteadOfARepetition() throws Exception {
        // black already holds 3-4-5 in the layout (no win is looked for there): it breaks the line and
        // rebuilds it on the move that brings the position back, which is a win by line, not a draw
        ThreeMenMorris game = ThreeMenMorris.fromLayout(GameId.generate(), "W.W" + "BBB" + "W..", 0, 0, WHITE);
        game.move(WHITE, 2, 1);
        game.move(BLACK, 5, 8);
        game.move(WHITE, 1, 2);

        game.move(BLACK, 8, 5);

        assertThat(game.result(), is(new MorrisResult(BLACK, MorrisResult.Reason.LINE)));
        assertThat(game.winningLine(), is(List.of(3, 4, 5)));
    }
}
