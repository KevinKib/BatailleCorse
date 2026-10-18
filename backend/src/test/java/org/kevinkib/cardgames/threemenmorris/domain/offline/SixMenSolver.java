package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules.FlyingRule;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules.LineRule;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A dense, layered retrograde solver for the {@code MILL} rule sets (Six Men's Morris), for offline use only
 * (test sources; nothing of it ships). A position is seen from the seat to move: the piece set of the mover
 * and of the opponent as 16-bit masks, and the two hands. Because the turn always passes after a move (a
 * mill's removal is part of the move, one removal choice per successor), there is no pending-removal state.
 *
 * <p><b>Layers.</b> A layer fixes the totals (board plus hand) of each player and the hands left to place
 * ({@code handsLeft}); the hands of the mover and the opponent follow ({@code handMe = ceil(s/2)},
 * {@code handOpp = floor(s/2)}: White places first). A state is indexed by the combinatorial rank of the
 * mover's set among all sets of its size, then of the opponent's set among the points left free. A removal
 * lowers a total (an earlier layer: layers are solved by increasing total), a placement lowers the hands (an
 * earlier layer of the same total), and only the movement layers (hands empty) are cyclic: they are solved by
 * rounds, round {@code r} resolving the wins and losses of depth {@code r} from the shallower ones.
 *
 * <p><b>Values.</b> One {@code short} per state: 0 unresolved (a draw: infinite play, which the engine's
 * repetition and quiet-move rules cut short, or a draw of the relaxed game), {@code +d} a win in {@code d}
 * plies for the mover, {@code -(d+1)} a loss in {@code d} plies (a blocked mover loses in 0). A player left
 * with two pieces (board plus hand) has lost; that terminal is never stored, it is the successor {@code -1}.
 */
final class SixMenSolver {

    /** The states with the given totals and hands; {@code size} of them from {@code offset}. */
    record Layer(int totalMe, int totalOpp, int handsLeft, int handMe, int handOpp, int onBoardMe,
                 int onBoardOpp, int offset, long size) {
    }

    /** A position from the seat to move: masks of the two piece sets and the two hands. */
    record Pos(int me, int opp, int handMe, int handOpp) {
    }

    /** One move (with its removal): the position for the opponent, or null when it ends the game. */
    record Succ(Pos next, boolean immediateWin) {
    }

    /** {@code outcome} is +1 (the mover wins), -1 (loses) or 0 (draw); {@code depth} is in plies. */
    record Verdict(int outcome, int depth) {
    }

    private static final int TERMINAL = -1;

    private final int points;
    private final int full;
    private final int pieces;
    private final int[] neighbourMask;
    private final int[] lineMasks;
    private final int[][] linesThrough;
    private final long[][] binomial;
    /** masksBySize[k] lists, in increasing order, the masks of the geometry that hold k points. */
    private final int[][] masksBySize;
    private final int[] rank;
    private final List<Layer> layers = new ArrayList<>();
    private final Layer[][][] layerOf;
    private final int stateCount;
    private short[] table;
    private final boolean[] winDepthSeen = new boolean[1 << 14];
    private final boolean[] lossDepthSeen = new boolean[1 << 14];
    private int maxDepth;

    SixMenSolver(MorrisRules rules) {
        if (rules.lineRule() != LineRule.MILL || rules.flying() != FlyingRule.NEVER) {
            throw new IllegalArgumentException("The offline solver plays mill rules without flying");
        }
        Geometry geometry = rules.geometry();
        this.points = geometry.points();
        this.full = (1 << points) - 1;
        this.pieces = rules.piecesPerPlayer();
        this.neighbourMask = new int[points];
        for (int p = 0; p < points; p++) {
            for (int q : geometry.neighbours(p)) {
                neighbourMask[p] |= 1 << q;
            }
        }
        this.lineMasks = geometry.lines().stream()
                .mapToInt(line -> line.stream().mapToInt(p -> 1 << p).reduce(0, (a, b) -> a | b)).toArray();
        this.linesThrough = new int[points][];
        for (int p = 0; p < points; p++) {
            final int point = p;
            linesThrough[p] = IntStream.of(lineMasks).filter(mask -> (mask & (1 << point)) != 0).toArray();
        }
        this.binomial = new long[points + 1][points + 1];
        for (int n = 0; n <= points; n++) {
            binomial[n][0] = 1;
            for (int k = 1; k <= n; k++) {
                binomial[n][k] = binomial[n - 1][k - 1] + (k <= n - 1 ? binomial[n - 1][k] : 0);
            }
        }
        this.masksBySize = new int[points + 1][];
        this.rank = new int[1 << points];
        int[] filled = new int[points + 1];
        for (int k = 0; k <= points; k++) {
            masksBySize[k] = new int[(int) binomial[points][k]];
        }
        for (int mask = 0; mask <= full; mask++) {
            int k = Integer.bitCount(mask);
            rank[mask] = filled[k];
            masksBySize[k][filled[k]++] = mask;
        }
        this.layerOf = new Layer[pieces + 1][pieces + 1][2 * pieces + 1];
        long offset = 0;
        for (int total = 6; total <= 2 * pieces; total++) {
            for (int handsLeft = 0; handsLeft <= 2 * pieces; handsLeft++) {
                for (int totalMe = 3; totalMe <= pieces; totalMe++) {
                    int totalOpp = total - totalMe;
                    int handMe = (handsLeft + 1) / 2;
                    int handOpp = handsLeft / 2;
                    int onBoardMe = totalMe - handMe;
                    int onBoardOpp = totalOpp - handOpp;
                    if (totalOpp < 3 || totalOpp > pieces || onBoardMe < 0 || onBoardOpp < 0
                            || onBoardMe + onBoardOpp > points) {
                        continue;
                    }
                    long size = binomial[points][onBoardMe] * binomial[points - onBoardMe][onBoardOpp];
                    Layer layer = new Layer(totalMe, totalOpp, handsLeft, handMe, handOpp, onBoardMe,
                            onBoardOpp, (int) offset, size);
                    layers.add(layer);
                    layerOf[totalMe][totalOpp][handsLeft] = layer;
                    offset += size;
                }
            }
        }
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Too many states for a dense int index: " + offset);
        }
        this.stateCount = (int) offset;
    }

    List<Layer> layers() {
        return layers;
    }

    int stateCount() {
        return stateCount;
    }

    Pos start() {
        return new Pos(0, 0, pieces, pieces);
    }

    // ------------------------------------------------------------------ index

    int indexOf(Pos pos) {
        return index(pos.me(), pos.opp(), pos.handMe(), pos.handOpp());
    }

    private int index(int me, int opp, int handMe, int handOpp) {
        int totalMe = Integer.bitCount(me) + handMe;
        int totalOpp = Integer.bitCount(opp) + handOpp;
        int handsLeft = handMe + handOpp;
        Layer layer = totalMe < 3 || totalMe > pieces || totalOpp < 3 || totalOpp > pieces
                || handsLeft > 2 * pieces ? null : layerOf[totalMe][totalOpp][handsLeft];
        if (layer == null || (me & opp) != 0 || handMe != layer.handMe() || handOpp != layer.handOpp()) {
            throw new IllegalArgumentException("Not a position of this game: " + new Pos(me, opp, handMe, handOpp));
        }
        int free = full & ~me;
        return layer.offset()
                + rank[me] * (int) binomial[points - layer.onBoardMe()][layer.onBoardOpp()]
                + rank[compress(opp, free)];
    }

    Pos decode(int index) {
        int low = 0;
        int high = layers.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (layers.get(mid).offset() <= index) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return decodeIn(layers.get(low), index);
    }

    private Pos decodeIn(Layer layer, int index) {
        int local = index - layer.offset();
        int oppSets = (int) binomial[points - layer.onBoardMe()][layer.onBoardOpp()];
        int me = masksBySize[layer.onBoardMe()][local / oppSets];
        int opp = deposit(masksBySize[layer.onBoardOpp()][local % oppSets], full & ~me);
        return new Pos(me, opp, layer.handMe(), layer.handOpp());
    }

    /** The bits of {@code value} at the positions set in {@code mask}, packed to the low end. */
    private static int compress(int value, int mask) {
        int result = 0;
        int bit = 0;
        for (int m = mask; m != 0; m &= m - 1) {
            if ((value & (m & -m)) != 0) {
                result |= 1 << bit;
            }
            bit++;
        }
        return result;
    }

    /** The inverse of {@link #compress}: spreads the low bits of {@code value} over the set bits of mask. */
    private static int deposit(int value, int mask) {
        int result = 0;
        int bit = 0;
        for (int m = mask; m != 0; m &= m - 1) {
            if ((value & (1 << bit)) != 0) {
                result |= m & -m;
            }
            bit++;
        }
        return result;
    }

    // ------------------------------------------------------------------ rules

    /** All moves of the seat to move, one per (move, removal) pair; allocation-free core of the solver. */
    private int generate(int me, int opp, int handMe, int handOpp, int[] out) {
        int count = 0;
        int free = full & ~(me | opp);
        int nextHandOpp = handMe > 0 ? handMe - 1 : 0;
        if (handMe > 0) {
            for (int f = free; f != 0; f &= f - 1) {
                int point = Integer.numberOfTrailingZeros(f);
                count = emit(me | (1 << point), point, opp, handOpp, nextHandOpp, out, count);
            }
        } else {
            for (int own = me; own != 0; own &= own - 1) {
                int from = Integer.numberOfTrailingZeros(own);
                for (int dest = neighbourMask[from] & free; dest != 0; dest &= dest - 1) {
                    int to = Integer.numberOfTrailingZeros(dest);
                    count = emit((me & ~(1 << from)) | (1 << to), to, opp, handOpp, nextHandOpp, out, count);
                }
            }
        }
        return count;
    }

    private int emit(int newMe, int point, int opp, int handOpp, int nextHandOpp, int[] out, int count) {
        boolean mill = false;
        for (int line : linesThrough[point]) {
            if ((newMe & line) == line) {
                mill = true;
                break;
            }
        }
        if (!mill || opp == 0) {
            out[count++] = index(opp, newMe, handOpp, nextHandOpp);
            return count;
        }
        int inMills = 0;
        for (int line : lineMasks) {
            if ((opp & line) == line) {
                inMills |= line;
            }
        }
        int candidates = opp & ~inMills;
        if (candidates == 0) {
            candidates = opp;
        }
        boolean reduces = Integer.bitCount(opp) - 1 + handOpp <= 2;
        for (int c = candidates; c != 0; c &= c - 1) {
            out[count++] = reduces ? TERMINAL : index(opp & ~(c & -c), newMe, handOpp, nextHandOpp);
        }
        return count;
    }

    List<Succ> successors(Pos pos) {
        int[] out = new int[1024];
        int count = generate(pos.me(), pos.opp(), pos.handMe(), pos.handOpp(), out);
        List<Succ> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(out[i] == TERMINAL ? new Succ(null, true) : new Succ(decode(out[i]), false));
        }
        return result;
    }

    // ------------------------------------------------------------------ solving

    void solve() {
        table = new short[stateCount];
        lossDepthSeen[0] = true;
        int index = 0;
        while (index < layers.size()) {
            Layer first = layers.get(index);
            int end = index;
            while (end < layers.size() && sameGroup(first, layers.get(end))) {
                end++;
            }
            List<Layer> group = layers.subList(index, end);
            if (first.handsLeft() == 0) {
                solveMovement(group);
            } else {
                group.forEach(this::solveAcyclic);
            }
            index = end;
        }
    }

    private static boolean sameGroup(Layer a, Layer b) {
        return a.totalMe() + a.totalOpp() == b.totalMe() + b.totalOpp() && a.handsLeft() == b.handsLeft();
    }

    private void note(short value) {
        if (value > 0) {
            winDepthSeen[value] = true;
            maxDepth = Math.max(maxDepth, value);
        } else if (value < 0) {
            lossDepthSeen[-value - 1] = true;
            maxDepth = Math.max(maxDepth, -value - 1);
        }
    }

    /** Placement layers: every successor is in an earlier layer, so one pass gives the exact value. */
    private void solveAcyclic(Layer layer) {
        int oppSets = (int) binomial[points - layer.onBoardMe()][layer.onBoardOpp()];
        IntStream.range(0, masksBySize[layer.onBoardMe()].length).parallel().forEach(rw -> {
            int[] out = new int[1024];
            int me = masksBySize[layer.onBoardMe()][rw];
            int free = full & ~me;
            for (int rb = 0; rb < oppSets; rb++) {
                int opp = deposit(masksBySize[layer.onBoardOpp()][rb], free);
                int count = generate(me, opp, layer.handMe(), layer.handOpp(), out);
                int bestWin = Integer.MAX_VALUE;
                int maxWon = -1;
                boolean allWon = true;
                for (int i = 0; i < count; i++) {
                    int v = out[i] == TERMINAL ? -1 : table[out[i]];
                    if (v < 0) {
                        bestWin = Math.min(bestWin, -v);
                    } else if (v > 0) {
                        maxWon = Math.max(maxWon, v);
                    } else {
                        allWon = false;
                    }
                }
                short value = bestWin != Integer.MAX_VALUE ? (short) bestWin : allWon ? (short) -(maxWon + 2) : 0;
                table[layer.offset() + rw * oppSets + rb] = value;
                note(value);
            }
        });
    }

    /** Movement layers of one total: they feed each other, so resolve them round by round. */
    private void solveMovement(List<Layer> group) {
        for (int round = 0; round == 0 || winDepthSeen[round - 1] || lossDepthSeen[round - 1]; round++) {
            final int r = round;
            for (Layer layer : group) {
                int oppSets = (int) binomial[points - layer.onBoardMe()][layer.onBoardOpp()];
                IntStream.range(0, masksBySize[layer.onBoardMe()].length).parallel().forEach(rw -> {
                    int[] out = new int[1024];
                    int me = masksBySize[layer.onBoardMe()][rw];
                    int free = full & ~me;
                    for (int rb = 0; rb < oppSets; rb++) {
                        int slot = layer.offset() + rw * oppSets + rb;
                        if (table[slot] != 0) {
                            continue;
                        }
                        int opp = deposit(masksBySize[layer.onBoardOpp()][rb], free);
                        int count = generate(me, opp, 0, 0, out);
                        boolean allWon = true;
                        int maxWon = -1;
                        boolean wins = false;
                        for (int i = 0; i < count && !wins; i++) {
                            int v = out[i] == TERMINAL ? -1 : table[out[i]];
                            if (r > 0 && v == -r) {
                                wins = true;
                            } else if (v > 0) {
                                maxWon = Math.max(maxWon, v);
                            } else {
                                allWon = false;
                            }
                        }
                        short value = wins ? (short) r : allWon && maxWon <= r - 1 ? (short) -(r + 1) : 0;
                        if (value != 0) {
                            table[slot] = value;
                            note(value);
                        }
                    }
                });
            }
        }
    }

    Verdict verdict(Pos pos) {
        return verdictOf(table[indexOf(pos)]);
    }

    private static Verdict verdictOf(short value) {
        return value > 0 ? new Verdict(1, value) : value < 0 ? new Verdict(-1, -value - 1) : new Verdict(0, 0);
    }

    // ------------------------------------------------------------------ report

    /** Values, reachability from the opening and the facts the spec records. */
    String report() {
        StringBuilder text = new StringBuilder();
        Verdict opening = verdict(start());
        text.append(String.format("opening (White to move, empty board): outcome %d (1 White wins, 0 draw, -1 Black wins), depth %d plies%n",
                opening.outcome(), opening.depth()));
        int[] out = new int[1024];
        int startIndex = indexOf(start());
        int count = generate(0, 0, pieces, pieces, out);
        for (int i = 0; i < count; i++) {
            Verdict child = verdictOf(table[out[i]]);
            text.append(String.format("  first placement at %d: Black to move %s%n",
                    Integer.numberOfTrailingZeros(decode(out[i]).opp()), describe(child)));
        }

        long wins = 0;
        long losses = 0;
        long draws = 0;
        int longestWin = 0;
        int longestLoss = 0;
        for (int i = 0; i < stateCount; i++) {
            short v = table[i];
            if (v > 0) {
                wins++;
                longestWin = Math.max(longestWin, v);
            } else if (v < 0) {
                losses++;
                longestLoss = Math.max(longestLoss, -v - 1);
            } else {
                draws++;
            }
        }
        text.append(String.format("all %d states: mover wins %d, loses %d, draws %d; longest win %d plies, longest loss %d plies%n",
                stateCount, wins, losses, draws, longestWin, longestLoss));

        BitSet reachable = reachable(startIndex);
        long reached = reachable.cardinality();
        long rWins = 0;
        long rLosses = 0;
        long rDraws = 0;
        long blocked = 0;
        long blockedMovement = 0;
        long reducing = 0;
        long placementReached = 0;
        for (int i = reachable.nextSetBit(0); i >= 0; i = reachable.nextSetBit(i + 1)) {
            short v = table[i];
            if (v > 0) {
                rWins++;
            } else if (v < 0) {
                rLosses++;
            } else {
                rDraws++;
            }
            Pos pos = decode(i);
            if (pos.handMe() > 0) {
                placementReached++;
            }
            int n = generate(pos.me(), pos.opp(), pos.handMe(), pos.handOpp(), out);
            if (n == 0) {
                blocked++;
            }
            for (int k = 0; k < n; k++) {
                if (out[k] == TERMINAL) {
                    reducing++;
                    break;
                }
            }
        }
        text.append(String.format("reachable from the opening (all legal play, stored states: at least 3 pieces each): %d "
                + "(placement phase %d, movement phase %d)%n", reached, placementReached, reached - placementReached));
        text.append(String.format("  of which the mover wins %d, loses %d, draws %d%n", rWins, rLosses, rDraws));
        text.append(String.format("  BLOCKED positions reachable (mover has no move): %d%n", blocked));
        text.append(String.format("  positions with a move that leaves the opponent two pieces (REDUCED end reachable): %d%n", reducing));
        text.append(String.format("deepest forced result: %d plies%n", maxDepth));
        return text.toString();
    }

    private static String describe(Verdict verdict) {
        return verdict.outcome() > 0 ? "wins in " + verdict.depth() : verdict.outcome() < 0
                ? "loses in " + verdict.depth() : "draws";
    }

    /** Every position reachable by legal play from the opening, by layers from the largest total down. */
    private BitSet reachable(int startIndex) {
        BitSet seen = new BitSet(stateCount);
        seen.set(startIndex);
        int[] out = new int[1024];
        int end = layers.size();
        while (end > 0) {
            int begin = end - 1;
            while (begin > 0 && sameGroup(layers.get(begin - 1), layers.get(end - 1))) {
                begin--;
            }
            boolean cyclic = layers.get(begin).handsLeft() == 0;
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int l = end - 1; l >= begin; l--) {
                    Layer layer = layers.get(l);
                    int limit = (int) (layer.offset() + layer.size());
                    for (int i = seen.nextSetBit(layer.offset()); i >= 0 && i < limit; i = seen.nextSetBit(i + 1)) {
                        Pos pos = decodeIn(layer, i);
                        int n = generate(pos.me(), pos.opp(), pos.handMe(), pos.handOpp(), out);
                        for (int k = 0; k < n; k++) {
                            if (out[k] != TERMINAL && !seen.get(out[k])) {
                                seen.set(out[k]);
                                changed |= cyclic;
                            }
                        }
                    }
                }
                changed &= cyclic;
            }
            end = begin;
        }
        return seen;
    }
}
