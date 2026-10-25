package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Capture;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Drop;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Pick;
import org.kevinkib.cardgames.mangola.domain.SowingStep.Relay;
import org.kevinkib.cardgames.mangola.domain.SowingStep.ResumeAfter;
import org.kevinkib.cardgames.mangola.domain.SowingStep.SkipEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the claim behind the sowing direction option: playing the loops the other way round is exactly the board
 * mirrored left to right, so the engine needs no notion of direction. The reference below sows with every pit's
 * next being its <em>predecessor</em> in the loop (same rules as {@link Sowing}, written independently of
 * {@link MangolaGeometry#next}); the engine, run on the mirrored board at the mirrored pit, must give the mirror of
 * that reference: board, endless flag, drops and trace. A future rule that depends on a column letter breaks this
 * test instead of silently breaking the option.
 */
class MangolaMirrorTest {

    private static final int BOARDS = 4_000;
    private static final MangolaRules RULES = MangolaRules.OWNER;

    /** The pit in the same row, the column swapped ({@code a} with {@code h}, {@code b} with {@code g} ...). */
    private static int m(int pit) {
        int row = pit / MangolaGeometry.COLUMNS;
        int column = pit % MangolaGeometry.COLUMNS;
        return row * MangolaGeometry.COLUMNS + (MangolaGeometry.COLUMNS - 1 - column);
    }

    private static MangolaBoard mirror(MangolaBoard board) {
        int[] mirrored = new int[MangolaGeometry.PITS];
        for (int pit = 0; pit < MangolaGeometry.PITS; pit++) {
            mirrored[m(pit)] = board.seeds(pit);
        }
        return MangolaBoard.of(mirrored);
    }

    private static SowingStep mirror(SowingStep step) {
        if (step instanceof Pick p) {
            return new Pick(m(p.pit()), p.count());
        } else if (step instanceof Drop d) {
            return new Drop(m(d.pit()));
        } else if (step instanceof SkipEmpty s) {
            return new SkipEmpty(m(s.pit()));
        } else if (step instanceof Capture c) {
            return new Capture(m(c.fromPit()), m(c.facing()), m(c.behind()), c.count());
        } else if (step instanceof Relay r) {
            return new Relay(m(r.pit()), r.count());
        } else if (step instanceof ResumeAfter r) {
            return new ResumeAfter(m(r.pit()));
        }
        throw new IllegalArgumentException("Unknown step " + step);
    }

    // ---------------------------------------------------------------- reference: the loops run backwards

    private static final int[] PREVIOUS = previousPits();

    private static int[] previousPits() {
        int[] previous = new int[MangolaGeometry.PITS];
        for (Side side : Side.values()) {
            List<Integer> loop = MangolaGeometry.loop(side);
            for (int i = 0; i < loop.size(); i++) {
                previous[loop.get(i)] = loop.get((i + loop.size() - 1) % loop.size());
            }
        }
        return previous;
    }

    /** {@link Sowing#sow} with the loop walked the other way round, everything else identical. */
    private static SowingResult reversedSow(MangolaBoard board, int pit) {
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
            if (savedSeeds == null) {
                savedSeeds = seeds.clone();
                savedPosition = position;
                savedHand = hand;
                savedLapStart = lapStart;
            } else {
                length++;
                if (position == savedPosition && hand == savedHand && lapStart == savedLapStart
                        && Arrays.equals(seeds, savedSeeds)) {
                    return new SowingResult(board, List.of(), true, drops);
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
                position = PREVIOUS[position];
                seeds[position]++;
                hand--;
                drops++;
                trace.add(new Drop(position));
            }
            if (drops > Sowing.MAX_DROPS) {
                return new SowingResult(board, List.of(), true, drops);
            }
            int landing = PREVIOUS[position];
            if (seeds[landing] == 0) {
                int after = PREVIOUS[landing];
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

    // ---------------------------------------------------------------- the property

    private static MangolaBoard randomBoard(Random random) {
        int[] seeds = new int[MangolaGeometry.PITS];
        for (Side side : Side.values()) {
            List<Integer> loop = MangolaGeometry.loop(side);
            // Sometimes spread over a few pits only, so that empty pits, laps and captures all occur.
            int used = 2 + random.nextInt(MangolaGeometry.LOOP_LENGTH - 1);
            for (int seed = 0; seed < Mangola.SEEDS_PER_SIDE; seed++) {
                seeds[loop.get(random.nextInt(used))]++;
            }
        }
        return MangolaBoard.of(seeds);
    }

    @Test
    void reversingTheLoopsIsTheMirroredBoardForEverySowing() {
        Random random = new Random(20_261_025L);
        int sowings = 0;
        int endless = 0;
        int captures = 0;

        for (int i = 0; i < BOARDS; i++) {
            MangolaBoard board = randomBoard(random);
            MangolaBoard mirrored = mirror(board);
            for (int pit = 0; pit < MangolaGeometry.PITS; pit++) {
                if (board.seeds(pit) < RULES.minSeedsToMove()) {
                    continue;
                }
                SowingResult reference = reversedSow(board, pit);
                SowingResult engine = Sowing.sow(mirrored, MangolaGeometry.owner(pit), m(pit), RULES);

                String where = "board " + i + ", pit " + MangolaGeometry.name(pit);
                assertEquals(reference.endless(), engine.endless(), where + ": endless");
                assertEquals(mirror(reference.board()), engine.board(), where + ": board");
                assertEquals(reference.drops(), engine.drops(), where + ": drops");
                assertEquals(reference.trace().stream().map(MangolaMirrorTest::mirror).toList(), engine.trace(),
                        where + ": trace");
                sowings++;
                endless += reference.endless() ? 1 : 0;
                captures += reference.captured() > 0 ? 1 : 0;
            }
        }

        // The generator must exercise the interesting cases, or the property would hold vacuously.
        assertTrue(sowings > 10_000, "sowings compared: " + sowings);
        assertTrue(captures > 100, "sowings with a capture: " + captures);
        assertTrue(endless > 0, "endless sowings: " + endless);
    }

    @Test
    void theMirrorIsAnInvolutionThatReversesEachLoop() {
        for (Side side : Side.values()) {
            List<Integer> loop = MangolaGeometry.loop(side);
            for (int i = 0; i < loop.size(); i++) {
                int mirroredNext = MangolaGeometry.next(m(loop.get(i)));
                assertEquals(m(loop.get((i + loop.size() - 1) % loop.size())), mirroredNext);
                assertEquals(loop.get(i), m(m(loop.get(i))));
            }
        }
    }
}
