package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Expert: negamax with alpha-beta pruning and iterative deepening under a time budget and a node cap, the moves
 * ordered by captures (the best move of the previous depth first). A position is 32 pit counts: there is no
 * table of solved positions to build, so this level is strong but not perfect.
 *
 * <p>The evaluation, from the side to move: the seeds on its half minus the opponent's, the pits that can be
 * sown (two seeds or more) on each side, and the captures on offer: a column where the opponent's inner pit is
 * occupied and the inner and outer pits of the other side are both occupied can be taken by the opponent's last
 * seed (exposure); the same column seen from the other side is an opportunity. A finished game is worth
 * {@code +-}{@link #WIN} minus the plies it took, so the quickest win and the slowest loss are preferred; a
 * sowing that never ends, when the rules make it a draw, is worth 0.
 *
 * <p>The layout is not searched: it is drawn from the placement book ({@link MangolaPlacementBook}), a fixed list per\n * side with a probability for each layout, frozen from the Mangola placement study\n * (docs/specs/2026-10-25-mangola-placement-study.md). Placement is hidden, so a good layout is a mixed strategy;\n * the study found that this mix beats the earlier layout search (0.605 against it) at no cost against weaker\n * placements, and it needs no time budget.
 */
public final class ExpertMangolaBot extends AbstractMangolaBot {

    /** The value of a won game; a won game in {@code n} plies is {@code WIN - n}. */
    public static final int WIN = 1_000_000;

    private static final int INFINITY = Integer.MAX_VALUE / 2;
    private static final int MAX_DEPTH = 64;
    private static final int CLOCK_CHECK_MASK = 63;

    private static final int SEED_WEIGHT = 10;
    private static final int MOBILITY_WEIGHT = 3;
    private static final int OPPORTUNITY_WEIGHT = 5;
    private static final int EXPOSURE_WEIGHT = 3;

    /**
     * What a decision may spend: wall-clock milliseconds for a move, a cap on the nodes searched (tests), and
     * wall-clock milliseconds for choosing a layout. The first depth is always completed, so the bot always
     * answers, even with nothing left.
     */
    public record Budget(long timeMillis, long nodeCap, long layoutMillis) {
        public Budget(long timeMillis, long nodeCap) {
            this(timeMillis, nodeCap, timeMillis * 3);
        }
    }

    /** The outcome of a search: the pit, its score for the side to move, the depth completed and the nodes spent. */
    public record SearchResult(int pit, int score, int depth, long nodes) {
    }

    private final Budget budget;

    public ExpertMangolaBot(RandomGenerator random, Budget budget) {
        super(random);
        this.budget = budget;
    }

    @Override
    protected int choose(MangolaPosition position, Side side, List<MangolaMoves.Move> moves) {
        if (moves.size() == 1) {
            return moves.get(0).pit(); // nothing to decide: do not spend the budget
        }
        return search(position, side).pit();
    }

    /** Searches the position for the side to move. */
    public SearchResult search(MangolaPosition position, Side side) {
        MangolaBoard board = MangolaMoves.boardOf(position);
        return new Search(position.rules(), budget.timeMillis(), budget.nodeCap()).run(board, side, random);
    }

    // ------------------------------------------------------------------ layout

    /** The layout is drawn from the placement book of the side, with the book's probabilities. */
    @Override
    protected int[] proposeLayout(MangolaRules rules, Side side, Set<List<Integer>> rejected) {
        return fromBook(rules, side, rejected, true);
    }

    // ------------------------------------------------------------------ search

    /** One iterative-deepening search: its counters and limits live here, so the bot itself holds no state. */
    private static final class Search {
        private final MangolaRules rules;
        private final long deadlineNanos;
        private final long nodeCap;
        private long nodes;
        private boolean abortable;
        private boolean aborted;

        Search(MangolaRules rules, long timeMillis, long nodeCap) {
            this.rules = rules;
            this.deadlineNanos = System.nanoTime() + Math.max(0, timeMillis) * 1_000_000L;
            this.nodeCap = nodeCap;
        }

        SearchResult run(MangolaBoard board, Side side, RandomGenerator random) {
            List<MangolaMoves.Move> root = new ArrayList<>(MangolaMoves.moves(board, side, rules));
            if (root.isEmpty()) {
                throw new IllegalStateException("No legal pit for side " + side);
            }
            order(root, -1);
            int bestPit = root.get(0).pit();
            int bestScore = 0;
            int completed = 0;
            for (int depth = 1; depth <= MAX_DEPTH; depth++) {
                abortable = depth > 1;
                List<MangolaMoves.Move> ties = new ArrayList<>();
                int iterationBest = -INFINITY;
                for (MangolaMoves.Move move : root) {
                    int score = scoreOfRootMove(move, side, depth, iterationBest);
                    if (aborted) {
                        break;
                    }
                    if (score > iterationBest) {
                        iterationBest = score;
                        ties.clear();
                    }
                    if (score == iterationBest) {
                        ties.add(move);
                    }
                }
                if (aborted) {
                    break;
                }
                MangolaMoves.Move chosen = ties.get(random.nextInt(ties.size()));
                bestPit = chosen.pit();
                bestScore = iterationBest;
                completed = depth;
                if (Math.abs(bestScore) >= WIN - MAX_DEPTH * 2) {
                    break; // the game is settled
                }
                order(root, bestPit);
            }
            return new SearchResult(bestPit, bestScore, completed, nodes);
        }

        /** The score of a root move, exact when it is at least the best so far (so that ties are found). */
        private int scoreOfRootMove(MangolaMoves.Move move, Side side, int depth, int best) {
            if (move.endless()) {
                return 0;
            }
            int alpha = best == -INFINITY ? -INFINITY : best - 1;
            return -negamax(move.board(), side.other(), depth - 1, -INFINITY, -alpha, 1);
        }

        /** Captures first, the move of the previous iteration before all. */
        private void order(List<MangolaMoves.Move> moves, int firstPit) {
            moves.sort(Comparator
                    .comparingInt((MangolaMoves.Move move) -> move.pit() == firstPit ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(MangolaMoves.Move::captured).reversed()));
        }

        private int negamax(MangolaBoard board, Side mover, int depth, int alpha, int beta, int ply) {
            nodes++;
            if (abortable && (nodes > nodeCap || (nodes & CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos)) {
                aborted = true;
                return 0;
            }
            if (depth == 0) {
                return leaf(board, mover, ply);
            }
            List<MangolaMoves.Move> moves = new ArrayList<>(MangolaMoves.moves(board, mover, rules));
            if (moves.isEmpty()) {
                return -(WIN - ply); // the player with no legal pit loses
            }
            order(moves, -1);
            int best = -INFINITY;
            for (MangolaMoves.Move move : moves) {
                int score = move.endless()
                        ? 0
                        : -negamax(move.board(), mover.other(), depth - 1, -beta, -alpha, ply + 1);
                if (aborted) {
                    return 0;
                }
                if (score > best) {
                    best = score;
                }
                if (best > alpha) {
                    alpha = best;
                }
                if (alpha >= beta) {
                    break;
                }
            }
            return best;
        }

        private int leaf(MangolaBoard board, Side mover, int ply) {
            if (sowablePits(board, mover) == 0) {
                return -(WIN - ply);
            }
            return evaluate(board, mover);
        }

        private int sowablePits(MangolaBoard board, Side side) {
            int count = 0;
            for (int pit : MangolaGeometry.loop(side)) {
                if (board.seeds(pit) >= rules.minSeedsToMove()) {
                    count++;
                }
            }
            return count;
        }

        /** The evaluation, from the side to move, described in the class comment. */
        private int evaluate(MangolaBoard board, Side mover) {
            Side other = mover.other();
            int seeds = board.sideTotal(mover) - board.sideTotal(other);
            int mobility = sowablePits(board, mover) - sowablePits(board, other);
            int opportunities = capturableColumns(board, mover);
            int exposure = capturableColumns(board, other);
            return SEED_WEIGHT * seeds + MOBILITY_WEIGHT * mobility
                    + OPPORTUNITY_WEIGHT * opportunities - EXPOSURE_WEIGHT * exposure;
        }

        /**
         * The columns where {@code attacker} could capture next: its inner pit holds seeds (a last seed can land
         * there), and the facing inner pit and the outer pit behind it, on the other half, are both occupied.
         */
        private int capturableColumns(MangolaBoard board, Side attacker) {
            int count = 0;
            for (int pit : MangolaGeometry.loop(attacker)) {
                if (MangolaGeometry.isInner(pit) && board.seeds(pit) > 0) {
                    int facing = MangolaGeometry.facing(pit);
                    int behind = MangolaGeometry.behind(pit);
                    if (board.seeds(facing) > 0 && board.seeds(behind) > 0) {
                        count++;
                    }
                }
            }
            return count;
        }
    }
}
