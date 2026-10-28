package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The placement book: for each side, a fixed list of starting layouts (32 seeds on the 16 pits of a loop, counts in
 * loop order) with the probability of playing each one.
 *
 * <p>The lists come from the Mangola placement study ({@code docs/specs/2026-10-25-mangola-placement-study.md}): the
 * pruned and renormalised fictitious-play mixes of the side-aware game over a pool of 161 layouts, after fifteen
 * oracle rounds (chunk S0, October 2026). The published probabilities are rounded to three decimals, so the weights
 * are normalised here, when the lists are frozen, to sum to exactly 1.
 *
 * <p>To regenerate them (after a change of rules or engine), run the study harness's {@code oracle} stage
 * (fifteen rounds, see the study) and then its {@code book} stage ({@code -Dmangola.study=book}, test scope; it
 * reads the {@code target/mangola-study/} cache the oracle wrote), which prints the lists as Java {@link Entry} lines, and paste them
 * below. The lists are data, with no property and no host option.
 */
final class MangolaPlacementBook {

    /** A named layout and its probability; {@link #counts()} returns a copy. */
    record Entry(String name, double weight, int[] counts) {
        Entry {
            counts = counts.clone();
        }

        @Override
        public int[] counts() {
            return counts.clone();
        }
    }

    private static final List<Entry> STARTER = normalised(List.of(
            entry("O6A2", 0.137, 2, 2, 1, 3, 3, 1, 2, 2, 4, 3, 2, 2, 1, 2, 1, 1),
            entry("EB1", 0.094, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 1, 8, 0, 0, 0, 7),
            entry("O9A1", 0.086, 0, 1, 0, 7, 0, 2, 0, 8, 0, 0, 0, 8, 0, 1, 0, 5),
            entry("O10B0", 0.067, 1, 0, 6, 0, 1, 0, 8, 2, 0, 0, 6, 0, 0, 0, 8, 0),
            entry("O14A2", 0.062, 0, 2, 0, 2, 2, 3, 2, 1, 0, 5, 6, 1, 0, 6, 0, 2),
            entry("O1A1", 0.056, 0, 4, 2, 2, 1, 2, 2, 3, 0, 3, 2, 3, 0, 3, 2, 3),
            entry("C04", 0.054, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2),
            entry("O14A0", 0.054, 0, 2, 2, 0, 0, 0, 5, 2, 0, 2, 6, 7, 2, 1, 2, 1),
            entry("O13A1", 0.051, 0, 7, 0, 0, 0, 6, 0, 0, 2, 8, 0, 1, 0, 8, 0, 0),
            entry("O15A2", 0.050, 4, 2, 0, 2, 0, 5, 2, 1, 0, 6, 1, 3, 3, 1, 1, 1),
            entry("O5A2", 0.045, 0, 3, 1, 1, 1, 4, 3, 5, 0, 2, 3, 2, 4, 1, 1, 1),
            entry("O14A1", 0.040, 1, 0, 5, 0, 1, 0, 8, 0, 1, 0, 8, 0, 0, 0, 8, 0),
            entry("O15A1", 0.038, 1, 0, 5, 0, 1, 1, 8, 0, 0, 0, 7, 0, 0, 1, 8, 0),
            entry("O11B0", 0.038, 0, 0, 4, 0, 1, 0, 8, 2, 2, 0, 6, 1, 0, 0, 8, 0),
            entry("C21", 0.035, 3, 2, 2, 1, 3, 2, 2, 1, 3, 2, 2, 1, 3, 2, 2, 1),
            entry("O8B2", 0.034, 1, 2, 0, 2, 1, 3, 3, 2, 3, 2, 3, 4, 0, 1, 2, 3),
            entry("O15B1", 0.031, 1, 0, 8, 1, 1, 0, 3, 0, 3, 0, 8, 2, 1, 0, 4, 0),
            entry("O11B1", 0.028, 2, 5, 0, 0, 0, 8, 0, 1, 0, 6, 0, 0, 2, 8, 0, 0)));

    private static final List<Entry> SECOND = normalised(List.of(
            entry("O10B0", 0.123, 1, 0, 6, 0, 1, 0, 8, 2, 0, 0, 6, 0, 0, 0, 8, 0),
            entry("O14B1", 0.118, 0, 0, 5, 0, 1, 0, 8, 0, 2, 0, 5, 1, 0, 0, 8, 2),
            entry("O15B0", 0.108, 0, 0, 5, 0, 1, 0, 9, 0, 2, 0, 4, 2, 0, 0, 8, 1),
            entry("O12B0", 0.099, 0, 0, 8, 2, 0, 0, 4, 0, 2, 1, 8, 2, 0, 0, 5, 0),
            entry("C20", 0.093, 0, 8, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0),
            entry("O9A1", 0.077, 0, 1, 0, 7, 0, 2, 0, 8, 0, 0, 0, 8, 0, 1, 0, 5),
            entry("O3B0", 0.072, 1, 0, 8, 2, 0, 0, 5, 0, 2, 0, 8, 0, 0, 0, 6, 0),
            entry("O4B1", 0.064, 0, 0, 7, 0, 1, 0, 4, 0, 3, 0, 8, 0, 0, 0, 7, 2),
            entry("O15B1", 0.058, 1, 0, 8, 1, 1, 0, 3, 0, 3, 0, 8, 2, 1, 0, 4, 0),
            entry("O6B0", 0.042, 0, 0, 7, 0, 1, 0, 5, 0, 3, 0, 7, 0, 1, 0, 7, 1),
            entry("O11B1", 0.038, 2, 5, 0, 0, 0, 8, 0, 1, 0, 6, 0, 0, 2, 8, 0, 0),
            entry("C19", 0.031, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 8, 0, 0, 0, 8, 0),
            entry("O6A0", 0.026, 0, 1, 0, 8, 0, 1, 0, 8, 0, 0, 0, 8, 0, 0, 0, 6),
            entry("O11B0", 0.026, 0, 0, 4, 0, 1, 0, 8, 2, 2, 0, 6, 1, 0, 0, 8, 0),
            entry("O9B0", 0.023, 0, 0, 6, 0, 0, 0, 8, 0, 3, 0, 7, 0, 0, 0, 8, 0)));

    private MangolaPlacementBook() {
    }

    /** The starter's list (side A). */
    static List<Entry> starter() {
        return STARTER;
    }

    /** The second player's list (side B). */
    static List<Entry> second() {
        return SECOND;
    }

    static List<Entry> of(Side side) {
        return side == Side.A ? STARTER : SECOND;
    }

    /** A layout of the side's list drawn with the list's probabilities, skipping rejected ones; empty if none is left. */
    static Optional<int[]> draw(Side side, RandomGenerator random, Set<List<Integer>> rejected) {
        return draw(side, random, rejected, true);
    }

    /** Same, with equal probabilities for the layouts that are left. */
    static Optional<int[]> drawUniform(Side side, RandomGenerator random, Set<List<Integer>> rejected) {
        return draw(side, random, rejected, false);
    }

    private static Optional<int[]> draw(Side side, RandomGenerator random, Set<List<Integer>> rejected,
                                        boolean weighted) {
        List<Entry> left = new ArrayList<>();
        double total = 0;
        for (Entry entry : of(side)) {
            if (!rejected.contains(MangolaLayouts.boxed(entry.counts()))) {
                left.add(entry);
                total += weighted ? entry.weight() : 1;
            }
        }
        if (left.isEmpty()) {
            return Optional.empty();
        }
        double target = random.nextDouble() * total;
        double cumulative = 0;
        for (Entry entry : left) {
            cumulative += weighted ? entry.weight() : 1;
            if (target < cumulative) {
                return Optional.of(entry.counts());
            }
        }
        return Optional.of(left.get(left.size() - 1).counts());
    }

    private static Entry entry(String name, double weight, int... counts) {
        return new Entry(name, weight, counts);
    }

    private static List<Entry> normalised(List<Entry> raw) {
        double sum = raw.stream().mapToDouble(Entry::weight).sum();
        return raw.stream().map(e -> new Entry(e.name(), e.weight() / sum, e.counts())).toList();
    }
}
