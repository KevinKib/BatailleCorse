package org.kevinkib.cardgames.draughts.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.DrawTracker;

/** The owner-approved early-draw rule: no draw before ply 20 unless a draw counter is running. */
class DraughtsEarlyDrawAnswerTest {

    static Stream<DraughtsBot> bots() {
        return Stream.of(
                new RandomDraughtsBot(new Random(1)),
                new GreedyDraughtsBot(new Random(1)),
                new ExpertDraughtsBot(new Random(1), new ExpertDraughtsBot.Budget(0, 2, 10_000)));
    }

    private static final DraughtsPosition EQUAL = BotFixtures.white("w32", "b12");
    private static final DraughtsPosition AHEAD = BotFixtures.white("w32", "w33", "b12");

    private static DraughtsDrawContext plies(int plies) {
        return new DraughtsDrawContext(plies, false);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void declinesBeforePly20EvenWhenEqual(DraughtsBot bot) {
        assertThat(bot.decide(EQUAL, Colour.WHITE, true, plies(0))).isInstanceOf(DraughtsDecision.DeclineDraw.class);
        assertThat(bot.decide(EQUAL, Colour.WHITE, true, plies(19))).isInstanceOf(DraughtsDecision.DeclineDraw.class);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void answersByMaterialFromPly20(DraughtsBot bot) {
        assertThat(bot.decide(EQUAL, Colour.WHITE, true, plies(20))).isInstanceOf(DraughtsDecision.AcceptDraw.class);
        assertThat(bot.decide(AHEAD, Colour.WHITE, true, plies(20))).isInstanceOf(DraughtsDecision.DeclineDraw.class);
        assertThat(bot.decide(AHEAD, Colour.BLACK, true, plies(20))).isInstanceOf(DraughtsDecision.AcceptDraw.class);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void aRunningCounterAnswersByMaterialEvenBeforePly20(DraughtsBot bot) {
        DraughtsDrawContext counter = new DraughtsDrawContext(4, true);

        assertThat(bot.decide(EQUAL, Colour.WHITE, true, counter)).isInstanceOf(DraughtsDecision.AcceptDraw.class);
        assertThat(bot.decide(AHEAD, Colour.WHITE, true, counter)).isInstanceOf(DraughtsDecision.DeclineDraw.class);
    }

    @ParameterizedTest
    @MethodSource("bots")
    void theThreeArgumentFormKeepsTheLateAnswer(DraughtsBot bot) {
        assertThat(bot.decide(EQUAL, Colour.WHITE, true)).isInstanceOf(DraughtsDecision.AcceptDraw.class);
    }

    @Test
    void theThresholdIsTwentyPlies() {
        assertThat(DraughtsDrawContext.MIN_PLIES_TO_ACCEPT_DRAW).isEqualTo(20);
    }

    @Test
    void aCounterIsRunningFromTheSecondOccurrenceTheShortEndingOrThirtyKingPlies() {
        DrawTracker fresh = DrawTracker.start(DraughtsPosition.initial());

        assertThat(DraughtsDrawContext.of(3, fresh, 1).counterRunning()).isFalse();
        assertThat(DraughtsDrawContext.of(3, fresh, 2).counterRunning()).isTrue();
        assertThat(DraughtsDrawContext.of(3, fresh, 2).plies()).isEqualTo(3);

        DrawTracker shortEnding = DrawTracker.start(BotFixtures.white("W32", "W33", "W34", "B12"));
        assertThat(DraughtsDrawContext.of(3, shortEnding, 1).counterRunning()).isTrue();
    }
}
