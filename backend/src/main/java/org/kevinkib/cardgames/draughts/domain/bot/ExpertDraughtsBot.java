package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.Piece;

/**
 * Expert: iterative-deepening negamax with alpha-beta, a shallow search by design (the owner does not want very
 * strong board-game bots). The previous depth's best move is searched first. A position where the side to move must
 * capture is not a leaf: the search goes on for a few extra plies so it never stops in the middle of an exchange.
 * The evaluation is material (man 100, king 300) plus small positional terms; a won position is worth
 * {@code WIN - ply}. The first depth always completes, so the bot always answers.
 */
public class ExpertDraughtsBot extends AbstractDraughtsBot {

    /**
     * What a decision may spend: wall-clock milliseconds, the deepest iteration, and a cap on the nodes searched (a
     * safety net, and the deterministic limit used by tests).
     */
    public record Budget(long timeMillis, int depthCap, long nodeCap) {
        public static final Budget DEFAULT = new Budget(300, 6, 2_000_000);
    }

    /** The outcome of a search: the move, its score for the side to move, the depth completed, nodes spent. */
    public record SearchResult(DraughtsMove move, int score, int depth, long nodes) {
    }

    static final int WIN = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MAX_EXTENSION = 4;
    private static final int CLOCK_CHECK_MASK = 63;

    private static final int MAN = 100;
    private static final int KING = 300;
    private static final int ADVANCE_PER_ROW = 3;
    private static final int CENTRE = 4;
    private static final int BACK_ROW = 6;
    /** MAN_BONUS[colour.ordinal()][square]: positional value of a man of that colour on that square. */
    private static final int[][] MAN_BONUS = new int[2][BoardGeometry.SQUARES + 1];

    static {
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            int row = BoardGeometry.row(square);
            int col = BoardGeometry.col(square);
            boolean centre = row >= 3 && row <= 6 && col >= 3 && col <= 6;
            for (Colour colour : Colour.values()) {
                boolean white = colour == Colour.WHITE;
                int advance = white ? BoardGeometry.SIZE - 1 - row : row;
                int bonus = advance * ADVANCE_PER_ROW + (centre ? CENTRE : 0);
                if (advance == 0) {
                    bonus += BACK_ROW;
                }
                MAN_BONUS[colour.ordinal()][square] = bonus;
            }
        }
    }

    private final RandomGenerator random;
    private final Budget budget;
    private final LongSupplier nanoClock;

    public ExpertDraughtsBot(RandomGenerator random, Budget budget) {
        this(random, budget, System::nanoTime);
    }

    /** The clock is injected so that tests control the time budget. */
    public ExpertDraughtsBot(RandomGenerator random, Budget budget, LongSupplier nanoClock) {
        this.random = random;
        this.budget = budget;
        this.nanoClock = nanoClock;
    }

    /** Searches the position for its side to move; ties between equal best moves go through the generator. */
    public SearchResult search(DraughtsPosition position, Colour me) {
        List<DraughtsMove> moves = generator.legalMoves(position);
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal move for " + position.toMove());
        }
        return new Search().run(position, moves);
    }

    @Override
    protected DraughtsMove choose(DraughtsPosition position, Colour me, List<DraughtsMove> moves) {
        return search(position, me).move();
    }

    /** The state of one decision. */
    private final class Search {
        private final long deadline = nanoClock.getAsLong() + budget.timeMillis() * 1_000_000L;
        private long nodes;
        private boolean aborted;
        private boolean mayAbort;

        SearchResult run(DraughtsPosition root, List<DraughtsMove> rootMoves) {
            List<DraughtsMove> ordered = rootMoves;
            List<DraughtsMove> candidates = List.of(rootMoves.get(0));
            int bestScore = 0;
            int completed = 0;
            for (int depth = 1; depth <= Math.max(1, budget.depthCap()); depth++) {
                if (depth > 1 && nanoClock.getAsLong() >= deadline) {
                    break;
                }
                mayAbort = depth > 1;
                aborted = false;
                int[] scores = new int[ordered.size()];
                List<DraughtsMove> best = new ArrayList<>();
                int top = -INFINITY;
                for (int i = 0; i < ordered.size() && !aborted; i++) {
                    DraughtsMove move = ordered.get(i);
                    int alpha = i == 0 ? -INFINITY : top - 1;
                    int score = -negamax(generator.apply(root, move), depth - 1, MAX_EXTENSION, -INFINITY, -alpha, 1);
                    scores[i] = score;
                    if (aborted) {
                        break;
                    }
                    if (score > top) {
                        top = score;
                        best.clear();
                    }
                    if (score == top) {
                        best.add(move);
                    }
                }
                if (aborted) {
                    break;
                }
                completed = depth;
                candidates = best;
                bestScore = top;
                ordered = reorder(ordered, scores, best.get(0));
            }
            DraughtsMove chosen = candidates.get(candidates.size() == 1 ? 0 : random.nextInt(candidates.size()));
            return new SearchResult(chosen, bestScore, completed, nodes);
        }

        /** Best move first, then by the score of the last iteration (stable, so the generator order breaks ties). */
        private List<DraughtsMove> reorder(List<DraughtsMove> moves, int[] scores, DraughtsMove best) {
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) {
                indexes.add(i);
            }
            indexes.sort(Comparator.comparingInt((Integer i) -> moves.get(i) == best ? 0 : 1)
                    .thenComparing(i -> -scores[i]));
            List<DraughtsMove> result = new ArrayList<>(moves.size());
            for (int i : indexes) {
                result.add(moves.get(i));
            }
            return result;
        }

        private int negamax(DraughtsPosition position, int depth, int extension, int alpha, int beta, int ply) {
            nodes++;
            if (mayAbort && shouldStop()) {
                aborted = true;
                return 0;
            }
            List<DraughtsMove> moves = generator.legalMoves(position);
            if (moves.isEmpty()) {
                return -(WIN - ply);
            }
            int childDepth = depth - 1;
            int childExtension = extension;
            if (depth <= 0) {
                if (extension <= 0 || !moves.get(0).isCapture()) {
                    return evaluate(position);
                }
                childDepth = 0;
                childExtension = extension - 1;
            }
            List<DraughtsMove> ordered = promotionsFirst(moves);
            int best = -INFINITY;
            for (DraughtsMove move : ordered) {
                int score = -negamax(generator.apply(position, move), childDepth, childExtension, -beta, -alpha,
                        ply + 1);
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

        private boolean shouldStop() {
            if (nodes > budget.nodeCap()) {
                return true;
            }
            return (nodes & CLOCK_CHECK_MASK) == 0 && nanoClock.getAsLong() >= deadline;
        }
    }

    private static List<DraughtsMove> promotionsFirst(List<DraughtsMove> moves) {
        if (moves.get(0).isCapture()) {
            return moves;
        }
        List<DraughtsMove> ordered = new ArrayList<>(moves);
        ordered.sort(Comparator.comparingInt(move -> move.promotes() ? 0 : 1));
        return ordered;
    }

    /** The value for the side to move: its material and positional terms minus the opponent's. */
    static int evaluate(DraughtsPosition position) {
        Colour toMove = position.toMove();
        int score = 0;
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == null) {
                continue;
            }
            int value = piece.isKing() ? KING : MAN + MAN_BONUS[piece.colour().ordinal()][square];
            score += piece.colour() == toMove ? value : -value;
        }
        return score;
    }
}
