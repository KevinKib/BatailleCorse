package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MangolaPlacementBookTest {

    private static final double TOLERANCE = 0.02;
    private static final int DRAWS = 10_000;

    @Test
    void theStarterListHoldsEighteenLayoutsAndTheSecondPlayerListFifteen() {
        assertThat(MangolaPlacementBook.starter()).hasSize(18);
        assertThat(MangolaPlacementBook.second()).hasSize(15);
        assertThat(MangolaPlacementBook.of(Side.A)).isEqualTo(MangolaPlacementBook.starter());
        assertThat(MangolaPlacementBook.of(Side.B)).isEqualTo(MangolaPlacementBook.second());
    }

    @Test
    void everyEntryIsALegalLayoutOfThirtyTwoSeedsOnSixteenPits() {
        for (Side side : Side.values()) {
            for (MangolaPlacementBook.Entry entry : MangolaPlacementBook.of(side)) {
                int[] counts = entry.counts();
                assertThat(counts).as(entry.name()).hasSize(16);
                assertThat(java.util.Arrays.stream(counts).min().orElseThrow()).as(entry.name()).isNotNegative();
                assertThat(BotFixtures.sum(counts)).as(entry.name()).isEqualTo(32);
                assertThat(Mangola.onlyEndlessMoves(MangolaRules.OWNER, side, BotFixtures.list(counts)))
                        .as(entry.name() + " as side " + side).isFalse();
            }
        }
    }

    @Test
    void theWeightsOfEachSideArePositiveAndSumToOne() {
        for (Side side : Side.values()) {
            double total = 0;
            for (MangolaPlacementBook.Entry entry : MangolaPlacementBook.of(side)) {
                assertThat(entry.weight()).as(entry.name()).isPositive();
                total += entry.weight();
            }
            assertThat(total).as("side " + side).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void noListHoldsTheSameNameOrTheSameLayoutTwice() {
        for (Side side : Side.values()) {
            List<MangolaPlacementBook.Entry> entries = MangolaPlacementBook.of(side);
            assertThat(entries.stream().map(MangolaPlacementBook.Entry::name).distinct().count())
                    .isEqualTo(entries.size());
            assertThat(entries.stream().map(e -> BotFixtures.list(e.counts())).distinct().count())
                    .isEqualTo(entries.size());
        }
    }

    @Test
    void theEntriesAreDefensiveCopies() {
        MangolaPlacementBook.starter().get(0).counts()[0] = 99;
        assertThat(MangolaPlacementBook.starter().get(0).counts()[0]).isNotEqualTo(99);
    }

    @Test
    void aDrawIsDeterministicForAGivenSeed() {
        for (Side side : Side.values()) {
            for (boolean weighted : new boolean[]{true, false}) {
                List<List<Integer>> first = draws(side, new SplittableRandom(42), 50, weighted, Set.of());
                List<List<Integer>> second = draws(side, new SplittableRandom(42), 50, weighted, Set.of());
                assertThat(first).isEqualTo(second);
            }
        }
    }

    @Test
    void aDrawReturnsALayoutOfTheSidesList() {
        for (Side side : Side.values()) {
            Set<List<Integer>> listed = layoutsOf(side);
            for (boolean weighted : new boolean[]{true, false}) {
                for (List<Integer> drawn : draws(side, new SplittableRandom(7), 200, weighted, Set.of())) {
                    assertThat(listed).contains(drawn);
                }
            }
        }
    }

    @Test
    void aRejectedLayoutIsNeverDrawnEvenWithAConstantGenerator() {
        for (Side side : Side.values()) {
            Set<List<Integer>> rejected = new HashSet<>();
            Set<List<Integer>> listed = layoutsOf(side);
            for (int i = 0; i < listed.size() - 1; i++) {
                Optional<int[]> drawn = MangolaPlacementBook.draw(side, BotFixtures.CONSTANT, rejected);
                assertThat(drawn).isPresent();
                assertThat(rejected).doesNotContain(BotFixtures.list(drawn.get()));
                rejected.add(BotFixtures.list(drawn.get()));
            }
            Set<List<Integer>> last = new HashSet<>(listed);
            last.removeAll(rejected);
            assertThat(last).hasSize(1);
            // One layout left: both draws must find it, whatever the generator says.
            assertThat(BotFixtures.list(MangolaPlacementBook.draw(side, BotFixtures.CONSTANT, rejected).orElseThrow()))
                    .isEqualTo(last.iterator().next());
            assertThat(BotFixtures.list(
                    MangolaPlacementBook.drawUniform(side, BotFixtures.CONSTANT, rejected).orElseThrow()))
                    .isEqualTo(last.iterator().next());
        }
    }

    @Test
    void whenEveryLayoutIsRejectedTheDrawIsEmpty() {
        for (Side side : Side.values()) {
            Set<List<Integer>> all = layoutsOf(side);
            assertThat(MangolaPlacementBook.draw(side, new SplittableRandom(1), all)).isEmpty();
            assertThat(MangolaPlacementBook.drawUniform(side, new SplittableRandom(1), all)).isEmpty();
        }
    }

    @Test
    void theWeightedDrawFollowsTheWeights() {
        for (Side side : Side.values()) {
            Map<List<Integer>, Integer> seen = frequencies(side, true);
            for (MangolaPlacementBook.Entry entry : MangolaPlacementBook.of(side)) {
                double frequency = seen.getOrDefault(BotFixtures.list(entry.counts()), 0) / (double) DRAWS;
                assertThat(frequency).as(entry.name() + " as side " + side)
                        .isCloseTo(entry.weight(), within(TOLERANCE));
            }
        }
    }

    @Test
    void theUniformDrawGivesEveryLayoutTheSameChance() {
        for (Side side : Side.values()) {
            Map<List<Integer>, Integer> seen = frequencies(side, false);
            int size = MangolaPlacementBook.of(side).size();
            for (MangolaPlacementBook.Entry entry : MangolaPlacementBook.of(side)) {
                double frequency = seen.getOrDefault(BotFixtures.list(entry.counts()), 0) / (double) DRAWS;
                assertThat(frequency).as(entry.name() + " as side " + side)
                        .isCloseTo(1.0 / size, within(TOLERANCE));
            }
        }
    }

    @Test
    void fromBookReturnsABookLayoutThatTheBotWouldAccept() {
        StubBot bot = new StubBot(new SplittableRandom(3));
        for (Side side : Side.values()) {
            Set<List<Integer>> listed = layoutsOf(side);
            for (int i = 0; i < 100; i++) {
                int[] layout = bot.fromBook(MangolaRules.OWNER, side, Set.of(), i % 2 == 0);
                assertThat(listed).contains(BotFixtures.list(layout));
            }
        }
    }

    @Test
    void fromBookFallsBackOnARandomOrPatternLayoutWhenEveryBookLayoutIsRejected() {
        StubBot bot = new StubBot(new SplittableRandom(3));
        int[] layout = bot.fromBook(MangolaRules.OWNER, Side.A, layoutsOf(Side.A), true);
        assertThat(layout).hasSize(16);
        assertThat(BotFixtures.sum(layout)).isEqualTo(32);
    }

    private static Set<List<Integer>> layoutsOf(Side side) {
        Set<List<Integer>> layouts = new HashSet<>();
        for (MangolaPlacementBook.Entry entry : MangolaPlacementBook.of(side)) {
            layouts.add(BotFixtures.list(entry.counts()));
        }
        return layouts;
    }

    private static List<List<Integer>> draws(Side side, RandomGenerator random, int count, boolean weighted,
                                             Set<List<Integer>> rejected) {
        List<List<Integer>> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Optional<int[]> layout = weighted
                    ? MangolaPlacementBook.draw(side, random, rejected)
                    : MangolaPlacementBook.drawUniform(side, random, rejected);
            drawn.add(BotFixtures.list(layout.orElseThrow()));
        }
        return drawn;
    }

    private static Map<List<Integer>, Integer> frequencies(Side side, boolean weighted) {
        Map<List<Integer>, Integer> seen = new LinkedHashMap<>();
        for (List<Integer> layout : draws(side, new SplittableRandom(2026), DRAWS, weighted, Set.of())) {
            seen.merge(layout, 1, Integer::sum);
        }
        return seen;
    }

    /** The smallest concrete bot: it only exposes the shared helper under test. */
    private static final class StubBot extends AbstractMangolaBot {
        StubBot(RandomGenerator random) {
            super(random);
        }

        @Override
        protected int[] proposeLayout(MangolaRules rules, Side side, Set<List<Integer>> rejected) {
            return fromBook(rules, side, rejected, true);
        }

        @Override
        protected int choose(MangolaPosition position, Side side, List<MangolaMoves.Move> moves) {
            return moves.get(0).pit();
        }
    }
}
