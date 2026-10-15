package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Layouts of 32 seeds on the 16 pits of a loop (counts in loop order): random ones and a catalogue of patterns. */
final class MangolaLayouts {

    static final int SEEDS = 32;

    /** Periodic patterns whose tiling over the 16 pits sums to 32 (the classic opening is {4,0}). */
    private static final int[][] PATTERNS = {
            {4, 0}, {3, 1}, {2, 2}, {4, 4, 0, 0}, {3, 3, 1, 1}, {6, 0, 2, 0}, {8, 0, 0, 0}, {3, 2, 2, 1},
            {4, 2, 1, 1}, {2, 3, 0, 3}, {4, 4, 4, 4, 0, 0, 0, 0}, {2, 2, 4, 0}, {5, 1, 1, 1}, {4, 0, 3, 1}
    };

    private static final List<int[]> CATALOGUE = buildCatalogue();

    private MangolaLayouts() {
    }

    /** The patterns and all their rotations around the loop, without duplicates, in a fixed order. */
    static List<int[]> catalogue() {
        return CATALOGUE;
    }

    /** 32 seeds dropped one by one on a uniformly drawn pit, as the random set-up does. */
    static int[] random(RandomGenerator random) {
        int[] counts = new int[MangolaGeometry.LOOP_LENGTH];
        for (int seed = 0; seed < SEEDS; seed++) {
            counts[random.nextInt(MangolaGeometry.LOOP_LENGTH)]++;
        }
        return counts;
    }

    private static List<int[]> buildCatalogue() {
        Set<List<Integer>> seen = new LinkedHashSet<>();
        List<int[]> layouts = new ArrayList<>();
        for (int[] pattern : PATTERNS) {
            for (int shift = 0; shift < MangolaGeometry.LOOP_LENGTH; shift++) {
                int[] counts = new int[MangolaGeometry.LOOP_LENGTH];
                int sum = 0;
                for (int i = 0; i < counts.length; i++) {
                    counts[i] = pattern[(i + shift) % pattern.length];
                    sum += counts[i];
                }
                if (sum == SEEDS && seen.add(boxed(counts))) {
                    layouts.add(counts);
                }
            }
        }
        return List.copyOf(layouts);
    }

    static List<Integer> boxed(int[] counts) {
        List<Integer> list = new ArrayList<>(counts.length);
        for (int count : counts) {
            list.add(count);
        }
        return list;
    }
}
