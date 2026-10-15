package org.kevinkib.cardgames.mangola.domain;

import java.util.Arrays;
import java.util.List;

/**
 * An immutable board: the seeds of each of the 32 pits. The game keeps 64 seeds on it, but the board
 * itself does not enforce a total, so that rules can be tested on small fixtures.
 */
public final class MangolaBoard {

    private static final MangolaBoard EMPTY = new MangolaBoard(new int[MangolaGeometry.PITS]);

    private final int[] seeds;

    private MangolaBoard(int[] seeds) {
        this.seeds = seeds;
    }

    public static MangolaBoard empty() {
        return EMPTY;
    }

    /** A board of 32 non-negative counts (the array is copied). */
    public static MangolaBoard of(int[] counts) {
        if (counts == null || counts.length != MangolaGeometry.PITS) {
            throw new IllegalArgumentException("A board has exactly " + MangolaGeometry.PITS + " pits");
        }
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("A pit cannot hold a negative number of seeds");
            }
        }
        return new MangolaBoard(counts.clone());
    }

    public int seeds(int pit) {
        if (!MangolaGeometry.isPit(pit)) {
            throw new IllegalArgumentException("No such pit: " + pit);
        }
        return seeds[pit];
    }

    public int sideTotal(Side side) {
        int total = 0;
        for (int pit : MangolaGeometry.loop(side)) {
            total += seeds[pit];
        }
        return total;
    }

    public int total() {
        return Arrays.stream(seeds).sum();
    }

    /** A copy with one pit set. */
    public MangolaBoard with(int pit, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("A pit cannot hold a negative number of seeds");
        }
        seeds(pit);
        int[] copy = seeds.clone();
        copy[pit] = count;
        return new MangolaBoard(copy);
    }

    /** A copy with a whole side replaced: 16 counts in the order of the side's loop. */
    public MangolaBoard withLayout(Side side, int[] counts) {
        if (counts == null || counts.length != MangolaGeometry.LOOP_LENGTH) {
            throw new IllegalArgumentException("A layout has exactly " + MangolaGeometry.LOOP_LENGTH + " counts");
        }
        int[] copy = seeds.clone();
        List<Integer> loop = MangolaGeometry.loop(side);
        for (int i = 0; i < loop.size(); i++) {
            if (counts[i] < 0) {
                throw new IllegalArgumentException("A pit cannot hold a negative number of seeds");
            }
            copy[loop.get(i)] = counts[i];
        }
        return new MangolaBoard(copy);
    }

    /** A copy of the 32 counts. */
    public int[] toArray() {
        return seeds.clone();
    }

    public List<Integer> toList() {
        return Arrays.stream(seeds).boxed().toList();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MangolaBoard board && Arrays.equals(seeds, board.seeds);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(seeds);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int pit = 0; pit < seeds.length; pit++) {
            if (seeds[pit] > 0) {
                text.append(text.length() > 0 ? " " : "").append(MangolaGeometry.name(pit)).append(':').append(seeds[pit]);
            }
        }
        return text.toString();
    }
}
