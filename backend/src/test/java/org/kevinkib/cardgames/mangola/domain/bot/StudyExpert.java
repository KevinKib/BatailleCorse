package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Placement study only (test scope): a copy of {@link ExpertMangolaBot} whose evaluation weights and layout node
 * cap are parameters, and whose budget is a node count only (no clock), so that every run is reproducible. With
 * {@link Weights#PRODUCTION} it searches and chooses layouts exactly like the production expert given the same
 * generator ({@code StudyExpertTest} checks it); any change to the production expert must be mirrored here.
 */
final class StudyExpert implements MangolaBot {

    /** A time budget no search reaches: the production expert given it is bounded by its node cap only. */
    static final long UNLIMITED_MILLIS = 1_000_000L;

    private static final int WIN = ExpertMangolaBot.WIN;
    private static final int INFINITY = Integer.MAX_VALUE / 2;
    private static final int MAX_DEPTH = 64;
    private static final int PRODUCTION_LAYOUT_NODE_CAP = 300;
    private static final int LAYOUT_CANDIDATES = 24;
    private static final int LAYOUT_MUTANTS = 24;
    private static final int PROPOSALS = 4;

    /** The evaluation weights: seeds, mobility, opportunity, exposure. */
    record Weights(int seed, int mobility, int opportunity, int exposure) {
        static final Weights PRODUCTION = new Weights(10, 3, 5, 3);
    }

    private final RandomGenerator random;
    private final long nodeCap;
    private final Weights weights;
    private final long layoutNodeCap;

    StudyExpert(RandomGenerator random, long nodeCap, Weights weights) {
        this(random, nodeCap, weights, PRODUCTION_LAYOUT_NODE_CAP);
    }

    StudyExpert(RandomGenerator random, long nodeCap, Weights weights, long layoutNodeCap) {
        this(random, nodeCap, weights, layoutNodeCap, List.of(), List.of());
    }

    /**
     * A hybrid for the study: {@code extraCandidates} are scored before the usual candidates and {@code
     * extraOpponents} join the typical opponents (empty lists give the production expert).
     */
    StudyExpert(RandomGenerator random, long nodeCap, Weights weights, long layoutNodeCap,
                List<int[]> extraCandidates, List<int[]> extraOpponents) {
        this.random = random;
        this.nodeCap = nodeCap;
        this.weights = weights;
        this.layoutNodeCap = layoutNodeCap;
        this.extraCandidates = extraCandidates;
        this.extraOpponents = extraOpponents;
    }

    private final List<int[]> extraCandidates;
    private final List<int[]> extraOpponents;

    // ------------------------------------------------------------------ the port

    @Override
    public int[] decideLayout(MangolaRules rules, Side side, java.util.Collection<List<Integer>> rejected) {
        if (!rejected.isEmpty()) {
            throw new UnsupportedOperationException("The study never refuses a layout");
        }
        return proposeLayout(rules, side);
    }

    @Override
    public int decideMove(MangolaPosition position, Side side) {
        MangolaBoard board = MangolaMoves.boardOf(position);
        List<MangolaMoves.Move> moves = MangolaMoves.moves(board, side, position.rules());
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal pit for side " + side);
        }
        if (moves.size() == 1) {
            return moves.get(0).pit();
        }
        return search(board, side, position.rules()).pit();
    }

    /** The search of the production expert, bounded by the node cap only. */
    ExpertMangolaBot.SearchResult search(MangolaBoard board, Side side, MangolaRules rules) {
        return new Search(rules, nodeCap).run(board, side);
    }

    // ------------------------------------------------------------------ layout

    /** {@code AbstractMangolaBot.decideLayout} with nothing refused, around the expert's proposal. */
    int[] proposeLayout(MangolaRules rules, Side side) {
        for (int proposal = 0; proposal < PROPOSALS; proposal++) {
            int[] layout = oneProposal(rules, side);
            if (acceptable(rules, side, layout)) {
                return layout;
            }
        }
        List<int[]> catalogue = MangolaLayouts.catalogue();
        int start = random.nextInt(catalogue.size());
        for (int i = 0; i < catalogue.size(); i++) {
            int[] layout = catalogue.get((start + i) % catalogue.size()).clone();
            if (acceptable(rules, side, layout)) {
                return layout;
            }
        }
        return MangolaLayouts.random(random);
    }

    private int[] oneProposal(MangolaRules rules, Side side) {
        List<int[]> opponents = new ArrayList<>(typicalOpponents());
        opponents.addAll(extraOpponents);
        List<int[]> catalogue = MangolaLayouts.catalogue();
        int[] best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int[] extra : extraCandidates) {
            if (acceptable(rules, side, extra)) {
                double score = layoutScore(rules, side, extra, opponents);
                if (best == null || score > bestScore) {
                    best = extra.clone();
                    bestScore = score;
                }
            }
        }
        int start = random.nextInt(catalogue.size());
        for (int i = 0; i < LAYOUT_CANDIDATES; i++) {
            int[] candidate = i < LAYOUT_CANDIDATES - 4
                    ? catalogue.get((start + i * 3) % catalogue.size()).clone()
                    : MangolaLayouts.random(random);
            if (!acceptable(rules, side, candidate)) {
                continue;
            }
            double score = layoutScore(rules, side, candidate, opponents);
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        for (int i = 0; best != null && i < LAYOUT_MUTANTS; i++) {
            int[] mutant = mutate(best);
            if (!acceptable(rules, side, mutant)) {
                continue;
            }
            double score = layoutScore(rules, side, mutant, opponents);
            if (score > bestScore) {
                best = mutant;
                bestScore = score;
            }
        }
        return best != null ? best : MangolaLayouts.random(random);
    }

    private static boolean acceptable(MangolaRules rules, Side side, int[] layout) {
        return !Mangola.onlyEndlessMoves(rules, side, MangolaLayouts.boxed(layout));
    }

    private int[] mutate(int[] layout) {
        int[] copy = layout.clone();
        int moves = 1 + random.nextInt(2);
        for (int i = 0; i < moves; i++) {
            int from = random.nextInt(copy.length);
            if (copy[from] == 0) {
                from = (from + 1) % copy.length;
                while (copy[from] == 0) {
                    from = (from + 1) % copy.length;
                }
            }
            copy[from]--;
            copy[random.nextInt(copy.length)]++;
        }
        return copy;
    }

    /** The expert's four typical opponents: the 1st and the middle catalogue layouts and two random ones. */
    List<int[]> typicalOpponents() {
        List<int[]> opponents = new ArrayList<>();
        List<int[]> catalogue = MangolaLayouts.catalogue();
        opponents.add(catalogue.get(0).clone());
        opponents.add(catalogue.get(catalogue.size() / 2).clone());
        opponents.add(MangolaLayouts.random(random));
        opponents.add(MangolaLayouts.random(random));
        return opponents;
    }

    /** The expert's layout score: the mean, for the side, of a shallow search from the first move. */
    double layoutScore(MangolaRules rules, Side side, int[] layout, List<int[]> opponents) {
        double total = 0;
        int counted = 0;
        for (int[] opponent : opponents) {
            MangolaBoard board = MangolaBoard.empty().withLayout(side, layout).withLayout(side.other(), opponent);
            if (MangolaMoves.moves(board, Side.A, rules).isEmpty()) {
                continue;
            }
            int fromA = new Search(rules, layoutNodeCap).run(board, Side.A).score();
            total += side == Side.A ? fromA : -fromA;
            counted++;
        }
        return counted == 0 ? 0 : total / counted;
    }

    // ------------------------------------------------------------------ search

    /** The evaluation of the production expert with this copy's weights, from the side to move. */
    int evaluate(MangolaBoard board, Side mover, MangolaRules rules) {
        return new Search(rules, 0).evaluate(board, mover);
    }

    private final class Search {
        private final MangolaRules rules;
        private final long cap;
        private long nodes;
        private boolean abortable;
        private boolean aborted;

        Search(MangolaRules rules, long cap) {
            this.rules = rules;
            this.cap = cap;
        }

        ExpertMangolaBot.SearchResult run(MangolaBoard board, Side side) {
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
                    break;
                }
                order(root, bestPit);
            }
            return new ExpertMangolaBot.SearchResult(bestPit, bestScore, completed, nodes);
        }

        private int scoreOfRootMove(MangolaMoves.Move move, Side side, int depth, int best) {
            if (move.endless()) {
                return 0;
            }
            int alpha = best == -INFINITY ? -INFINITY : best - 1;
            return -negamax(move.board(), side.other(), depth - 1, -INFINITY, -alpha, 1);
        }

        private void order(List<MangolaMoves.Move> moves, int firstPit) {
            moves.sort(Comparator
                    .comparingInt((MangolaMoves.Move move) -> move.pit() == firstPit ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(MangolaMoves.Move::captured).reversed()));
        }

        private int negamax(MangolaBoard board, Side mover, int depth, int alpha, int beta, int ply) {
            nodes++;
            if (abortable && nodes > cap) {
                aborted = true;
                return 0;
            }
            if (depth == 0) {
                return leaf(board, mover, ply);
            }
            List<MangolaMoves.Move> moves = new ArrayList<>(MangolaMoves.moves(board, mover, rules));
            if (moves.isEmpty()) {
                return -(WIN - ply);
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

        int evaluate(MangolaBoard board, Side mover) {
            Side other = mover.other();
            int seeds = board.sideTotal(mover) - board.sideTotal(other);
            int mobility = sowablePits(board, mover) - sowablePits(board, other);
            return weights.seed() * seeds + weights.mobility() * mobility
                    + weights.opportunity() * capturableColumns(board, mover)
                    - weights.exposure() * capturableColumns(board, other);
        }

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
