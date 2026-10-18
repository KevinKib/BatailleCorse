package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Q11: on Six a bot accepts a draw when it is not ahead in pieces left (board plus hand), declines otherwise. */
class SixMenDrawAnswerTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    @ParameterizedTest
    @EnumSource(MorrisBotLevel.class)
    void theSeatBehindInPiecesAcceptsAndTheOneAheadDeclines(MorrisBotLevel level) {
        MorrisPosition position = SixPositions.of("W.W.W.W.B.B.B.BB", 0, 0, 0);
        MorrisBot bot = level.bot(RandomGenerator.getDefault());

        assertThat(bot.decide(position, WHITE, true), is(new MorrisDecision.AcceptDraw()));
        assertThat(bot.decide(position, BLACK, true), is(new MorrisDecision.DeclineDraw()));
    }

    @ParameterizedTest
    @EnumSource(MorrisBotLevel.class)
    void equalPiecesAcceptOnBothSides(MorrisBotLevel level) {
        MorrisPosition position = SixPositions.of("W.W.W.W.B.B.B.B.", 0, 0, 0);
        MorrisBot bot = level.bot(RandomGenerator.getDefault());

        assertThat(bot.decide(position, WHITE, true), is(new MorrisDecision.AcceptDraw()));
        assertThat(bot.decide(position, BLACK, true), is(new MorrisDecision.AcceptDraw()));
    }

    @Test
    void piecesInHandCountAsPiecesLeft() {
        // Board 4 against 4, but white still holds 2 pieces: white is ahead and declines.
        MorrisPosition position = SixPositions.of("W.W.W.W.B.B.B.B.", 2, 0, 0);
        MorrisBot bot = new GreedyBot(Positions.FIRST);

        assertThat(bot.decide(position, WHITE, true), is(new MorrisDecision.DeclineDraw()));
        assertThat(bot.decide(position, BLACK, true), is(new MorrisDecision.AcceptDraw()));
    }
}
