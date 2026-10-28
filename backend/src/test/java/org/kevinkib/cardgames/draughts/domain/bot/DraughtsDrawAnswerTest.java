package org.kevinkib.cardgames.draughts.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;

class DraughtsDrawAnswerTest {

    static Stream<DraughtsBot> bots() {
        return Stream.of(
                new RandomDraughtsBot(new Random(1)),
                new GreedyDraughtsBot(new Random(1)),
                new ExpertDraughtsBot(new Random(1), new ExpertDraughtsBot.Budget(0, 2, 10_000)));
    }

    @ParameterizedTest
    @MethodSource("bots")
    void acceptsWhenMaterialIsEqual(DraughtsBot bot) {
        DraughtsPosition position = BotFixtures.white("w32", "b12");

        assertThat(bot.decide(position, Colour.WHITE, true)).isInstanceOf(DraughtsDecision.AcceptDraw.class);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void acceptsWhenBehind(DraughtsBot bot) {
        DraughtsPosition position = BotFixtures.white("w32", "b12", "b13");

        assertThat(bot.decide(position, Colour.WHITE, true)).isInstanceOf(DraughtsDecision.AcceptDraw.class);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void declinesWhenAhead(DraughtsBot bot) {
        DraughtsPosition position = BotFixtures.white("w32", "w33", "b12");

        assertThat(bot.decide(position, Colour.WHITE, true)).isInstanceOf(DraughtsDecision.DeclineDraw.class);
        assertThat(bot.decide(position, Colour.BLACK, true)).isInstanceOf(DraughtsDecision.AcceptDraw.class);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void aKingCountsThreeMen(DraughtsBot bot) {
        DraughtsPosition kingAgainstTwoMen = BotFixtures.white("W32", "b12", "b13");
        DraughtsPosition kingAgainstFourMen = BotFixtures.white("W32", "b12", "b13", "b14", "b15");

        assertThat(bot.decide(kingAgainstTwoMen, Colour.WHITE, true)).isInstanceOf(DraughtsDecision.DeclineDraw.class);
        assertThat(bot.decide(kingAgainstFourMen, Colour.WHITE, true)).isInstanceOf(DraughtsDecision.AcceptDraw.class);
    }

    @Test
    void materialCountsManOneKingThree() {
        DraughtsPosition position = BotFixtures.white("w32", "W33", "b12");

        assertThat(DraughtsMaterial.of(position, Colour.WHITE)).isEqualTo(4);
        assertThat(DraughtsMaterial.of(position, Colour.BLACK)).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void withoutALegalMoveThereIsNothingToDecide(DraughtsBot bot) {
        DraughtsPosition blocked = BotFixtures.white("b12");

        assertThatThrownBy(() -> bot.decide(blocked, Colour.WHITE, false)).isInstanceOf(IllegalStateException.class);
    }
}
