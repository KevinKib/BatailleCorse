package org.kevinkib.cardgames.draughts.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.MoveGenerator;
import org.kevinkib.cardgames.draughts.domain.bot.ExpertDraughtsBot.Budget;
import org.kevinkib.cardgames.draughts.domain.bot.ExpertDraughtsBot.SearchResult;

class ExpertDraughtsBotTest {

    /** A clock that never moves: no time pressure, only the depth and node caps apply. */
    private static final LongSupplier FROZEN = () -> 0L;

    /** White sacrifices 32-28; 22 must take it and 33 is retaken with a double capture, leaving Black nothing. */
    private static DraughtsPosition shot() {
        return BotFixtures.white("w32", "w38", "w42", "b22", "b24");
    }

    private static ExpertDraughtsBot expert(Budget budget) {
        return new ExpertDraughtsBot(BotFixtures.CONSTANT, budget, FROZEN);
    }

    @Test
    void findsTheTwoForOneShotThatGreedyMisses() {
        DraughtsPosition position = shot();

        SearchResult result = expert(new Budget(60_000, 4, 2_000_000)).search(position, Colour.WHITE);

        assertThat(result.move().notation()).isEqualTo("32-28");
        assertThat(result.score()).isGreaterThan(50_000);
        for (long seed = 0; seed < 30; seed++) {
            var greedy = (DraughtsDecision.Play) new GreedyDraughtsBot(new Random(seed))
                    .decide(position, Colour.WHITE, false);
            assertThat(greedy.move().notation()).isNotEqualTo("32-28");
        }
    }

    @Test
    void respectsTheDepthCap() {
        SearchResult result = expert(new Budget(60_000, 3, 100_000_000)).search(DraughtsPosition.initial(), Colour.WHITE);

        assertThat(result.depth()).isEqualTo(3);
    }

    @Test
    void respectsTheNodeCapButCompletesTheFirstDepth() {
        SearchResult result = expert(new Budget(60_000, 6, 1_000)).search(DraughtsPosition.initial(), Colour.WHITE);

        assertThat(result.depth()).isBetween(1, 5);
        assertThat(result.nodes()).isLessThanOrEqualTo(1_001);
        assertThat(new MoveGenerator().legalMoves(DraughtsPosition.initial())).contains(result.move());
    }

    @Test
    void aZeroBudgetStillAnswersAtDepthOne() {
        AtomicLong now = new AtomicLong();
        LongSupplier ticking = () -> now.addAndGet(1_000_000);
        ExpertDraughtsBot bot = new ExpertDraughtsBot(BotFixtures.CONSTANT, new Budget(0, 6, 2_000_000), ticking);

        SearchResult result = bot.search(DraughtsPosition.initial(), Colour.WHITE);

        assertThat(result.depth()).isEqualTo(1);
        assertThat(new MoveGenerator().legalMoves(DraughtsPosition.initial())).contains(result.move());
    }

    @Test
    void stopsWhenTheTimeBudgetIsSpent() {
        AtomicLong now = new AtomicLong();
        LongSupplier ticking = () -> now.addAndGet(100_000); // 0.1 ms per reading
        ExpertDraughtsBot bot = new ExpertDraughtsBot(BotFixtures.CONSTANT, new Budget(5, 8, 100_000_000), ticking);

        SearchResult result = bot.search(DraughtsPosition.initial(), Colour.WHITE);

        assertThat(result.depth()).isLessThan(8);
    }

    @Test
    void isDeterministicWithAConstantGeneratorAndAFrozenClock() {
        Budget budget = new Budget(60_000, 4, 2_000_000);

        for (DraughtsPosition position : BotFixtures.randomPositions(10, 11)) {
            assertThat(expert(budget).search(position, position.toMove()))
                    .isEqualTo(expert(budget).search(position, position.toMove()));
        }
    }

    @Test
    void alwaysLegalOnRandomPositions() {
        ExpertDraughtsBot bot = expert(new Budget(60_000, 2, 100_000));
        MoveGenerator generator = new MoveGenerator();
        for (DraughtsPosition position : BotFixtures.randomPositions(100, 21)) {
            var decision = (DraughtsDecision.Play) bot.decide(position, position.toMove(), false);

            assertThat(generator.legalMoves(position)).contains(decision.move());
        }
    }

    @Test
    void seesTheShotEvenAtDepthOneThanksToTheCaptureExtension() {
        SearchResult result = expert(new Budget(60_000, 1, 2_000_000)).search(shot(), Colour.WHITE);

        assertThat(result.move().notation()).isEqualTo("32-28");
    }
}
