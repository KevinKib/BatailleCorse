package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.mangola.domain.SowingStep.Capture;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Drop;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Pick;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Relay;
import org.kevinkib.cardgames.mangola.domain.SowingStep.ResumeAfter;
import org.kevinkib.cardgames.mangola.domain.SowingStep.SkipEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The move algorithm of the owner's game (exactly {@code Ngola.play} of {@code ngola.js}), as a pure
 * function. The seeds of the chosen pit go into the hand and are dropped one per pit along the side's
 * loop (a lap of 16 seeds or more drops into its own start pit like any other). The last seed of a lap:
 * if the next pit is empty it goes to the pit after it and the turn ends; otherwise it is dropped there,
 * and then either the pit is an inner pit whose facing and behind pits are both occupied (capture: both
 * are taken and sown from the pit after the lap's start, which does not change) or the whole pit is
 * taken and re-sown (relay: it becomes the start of the new lap).
 *
 * <p>A sowing can cycle for ever on the mover's own side (a capture lowers the opponent's seeds, so a
 * cycle never contains one). It is detected exactly with Brent's algorithm on the state at each lap
 * boundary (the 32 pits, the pit and the seeds in hand and the lap start), in constant memory; a cap on
 * the seeds dropped ({@link #MAX_DROPS}, the JS {@code MAX_REPEATS}) is a safety net.
 */
public final class Sowing {

    /** The JS {@code MAX_REPEATS}: more seeds dropped than this and a sowing counts as endless. */
    public static final int MAX_DROPS = 100_000;

    private Sowing() {
    }

    public static SowingResult sow(MangolaBoard board, Side side, int pit, MangolaRules rules) {
        return sow(board, side, pit, rules, MAX_DROPS);
    }

    /** Like {@link #sow(MangolaBoard, Side, int, MangolaRules)} with another cap (for tests). */
    static SowingResult sow(MangolaBoard board, Side side, int pit, MangolaRules rules, int maxDrops) {
        if (MangolaGeometry.owner(pit) != side) {
            throw new IllegalArgumentException("Pit " + MangolaGeometry.name(pit) + " is not on side " + side);
        }
        if (board.seeds(pit) < rules.minSeedsToMove()) {
            throw new IllegalArgumentException("Pit " + MangolaGeometry.name(pit) + " holds too few seeds");
        }
        int[] seeds = board.toArray();
        List<SowingStep> trace = new ArrayList<>();
        int hand = seeds[pit];
        seeds[pit] = 0;
        trace.add(new Pick(pit, hand));
        int lapStart = pit;
        int position = pit;
        int drops = 0;

        int[] savedSeeds = null;
        int savedPosition = 0;
        int savedHand = 0;
        int savedLapStart = 0;
        long power = 1;
        long length = 0;

        while (true) {
            // A lap begins: compare with the saved state (Brent), then save a new one at powers of two.
            if (savedSeeds == null) {
                savedSeeds = seeds.clone();
                savedPosition = position;
                savedHand = hand;
                savedLapStart = lapStart;
            } else {
                length++;
                if (position == savedPosition && hand == savedHand && lapStart == savedLapStart
                        && Arrays.equals(seeds, savedSeeds)) {
                    return endless(board, drops);
                }
                if (length == power) {
                    savedSeeds = seeds.clone();
                    savedPosition = position;
                    savedHand = hand;
                    savedLapStart = lapStart;
                    power *= 2;
                    length = 0;
                }
            }

            while (hand > 1) {
                position = MangolaGeometry.next(position);
                seeds[position]++;
                hand--;
                drops++;
                trace.add(new Drop(position));
            }
            if (drops > maxDrops) {
                return endless(board, drops);
            }

            int landing = MangolaGeometry.next(position);
            if (seeds[landing] == 0) {
                int after = MangolaGeometry.next(landing);
                seeds[after]++;
                drops++;
                trace.add(new SkipEmpty(landing));
                trace.add(new Drop(after));
                return new SowingResult(MangolaBoard.of(seeds), trace, false, drops);
            }

            seeds[landing]++;
            drops++;
            trace.add(new Drop(landing));
            position = landing;
            int facing = MangolaGeometry.facing(landing);
            int behind = MangolaGeometry.behind(landing);
            if (facing != MangolaGeometry.NONE && seeds[facing] > 0 && seeds[behind] > 0) {
                hand = seeds[facing] + seeds[behind];
                trace.add(new Capture(landing, facing, behind, hand));
                seeds[facing] = 0;
                seeds[behind] = 0;
                trace.add(new ResumeAfter(lapStart));
                position = lapStart;
            } else {
                hand = seeds[landing];
                seeds[landing] = 0;
                trace.add(new Relay(landing, hand));
                lapStart = landing;
            }
        }
    }

    private static SowingResult endless(MangolaBoard board, int drops) {
        return new SowingResult(board, List.of(), true, drops);
    }
}
