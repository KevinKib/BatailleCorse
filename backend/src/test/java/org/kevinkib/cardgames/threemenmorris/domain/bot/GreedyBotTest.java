package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GreedyBotTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final MorrisBot bot = new GreedyBot(Positions.FIRST);

    @Test
    void takesALineWhenItHasOneEvenIfTheOpponentThreatensToo() {
        // White 0 and 1, black 3 and 4 (black threatens 5): white completes its own row first.
        MorrisPosition position = Positions.tapatan("WW.BB....", 1, 1, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Place(2)));
    }

    @Test
    void blocksTheLineTheOpponentWouldMakeNext() {
        // Black 3 and 4 threaten 5; white 0 and 8 cannot win: it must take 5.
        MorrisPosition position = Positions.tapatan("W..BB...W", 1, 1, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Place(5)));
    }

    @Test
    void inTheMovementPhaseItCompletesALineByAMove() {
        // White 0, 1, 5 moves 5 to 2.
        MorrisPosition position = Positions.tapatan("WW.BBW..B", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Step(5, 2)));
    }

    @Test
    void inTheMovementPhaseItMakesTheOnlyMoveThatLeavesTheOpponentNoLine() {
        // Black 3, 4, 8 would play 8 to 5; the only white move that stops it is 2 to 5.
        MorrisPosition position = Positions.tapatan("W.WBB.W.B", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Step(2, 5)));
    }

    @Test
    void withNothingToTakeOrBlockItPlaysTheFirstLegalDecisionOfTheGenerator() {
        assertThat(bot.decide(Positions.tapatan(".........", 3, 3, 0), WHITE, false),
                is(new MorrisDecision.Place(0)));
    }

    @Test
    void itAnswersADrawOfferWithTheSolverRule() {
        // White to move wins at once by 5 to 2: white declines, black (who loses) accepts.
        MorrisPosition position = Positions.tapatan("WW.BBW..B", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, true), is(new MorrisDecision.DeclineDraw()));
        assertThat(bot.decide(position, BLACK, true), is(new MorrisDecision.AcceptDraw()));
    }
}
