package org.kevinkib.cardgames.draughts.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry.Direction;

/**
 * Pure, stateless rules engine of international draughts (FMJD rules, Annexe 1): the legal moves of a position and
 * the position after a move.
 * <ul>
 *   <li>Men move one square forward; kings slide any distance (art. 3).</li>
 *   <li>Capture is compulsory, for men and kings, forwards and backwards; a king captures at a distance and lands on
 *       any free square behind the captured piece; a rafle continues while a jump is possible (4.1 to 4.6).</li>
 *   <li>A piece is never jumped twice and captured pieces stay on the board, as obstacles, until the end of the
 *       move (4.8, 4.11); the square the capturing piece left is free.</li>
 *   <li>The largest number of captured pieces must be taken, a king counting as one (4.13, 4.14).</li>
 *   <li>A man is crowned only when its move ends on the far row (4.15).</li>
 * </ul>
 * Two sequences with the same start, end and captured set are one move; the same start and end with different
 * captured sets are different moves.
 */
public class MoveGenerator {

    private static final Direction[] DIRECTIONS = Direction.values();
    /** RAYS[square][direction.ordinal()]: read-only copy of the geometry tables, to avoid cloning in the search. */
    private static final int[][][] RAYS = new int[BoardGeometry.SQUARES + 1][DIRECTIONS.length][];

    static {
        for (int n = 1; n <= BoardGeometry.SQUARES; n++) {
            for (Direction d : DIRECTIONS) {
                RAYS[n][d.ordinal()] = BoardGeometry.ray(n, d);
            }
        }
    }

    private static final Comparator<DraughtsMove> ORDER = Comparator
            .comparingInt(DraughtsMove::from)
            .thenComparingInt(DraughtsMove::to)
            .thenComparing(MoveGenerator::sortedCaptures, MoveGenerator::compareLists)
            .thenComparing(DraughtsMove::path, MoveGenerator::compareLists);

    /** Every legal move for the colour to move, deterministic order (by from, to, captured squares). */
    public List<DraughtsMove> legalMoves(DraughtsPosition position) {
        Piece[] board = position.board();
        Colour me = position.toMove();

        List<DraughtsMove> captures = new ArrayList<>();
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = board[square];
            if (piece != null && piece.colour() == me) {
                collectCaptures(board, square, piece, captures);
            }
        }
        if (!captures.isEmpty()) {
            return majority(captures);
        }

        List<DraughtsMove> plain = new ArrayList<>();
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = board[square];
            if (piece != null && piece.colour() == me) {
                collectPlain(board, square, piece, plain);
            }
        }
        plain.sort(ORDER);
        return plain;
    }

    /** The position after {@code move}: pieces removed after the whole move, promotion, side to move swapped. */
    public DraughtsPosition apply(DraughtsPosition position, DraughtsMove move) {
        Piece piece = position.pieceAt(move.from());
        if (piece == null || piece.colour() != position.toMove()) {
            throw new IllegalArgumentException("No piece of the side to move on square " + move.from());
        }
        Piece[] board = position.board();
        board[move.from()] = null;
        for (int captured : move.captures()) {
            board[captured] = null;
        }
        board[move.to()] = move.promotes() ? crowned(piece) : piece;
        return new DraughtsPosition(board, position.toMove().opposite());
    }

    /**
     * Why the side to move has lost, if it has: no piece left (7.2.3) or no legal move (7.2.2). Empty while the game
     * goes on. The caller turns the reason into a winner (the opponent).
     */
    public Optional<DraughtsResult.Reason> lossReason(DraughtsPosition position) {
        boolean hasPiece = false;
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null && piece.colour() == position.toMove()) {
                hasPiece = true;
                break;
            }
        }
        if (!hasPiece) {
            return Optional.of(DraughtsResult.Reason.NO_PIECES);
        }
        return legalMoves(position).isEmpty() ? Optional.of(DraughtsResult.Reason.BLOCKED) : Optional.empty();
    }

    private static Piece crowned(Piece man) {
        return man.colour() == Colour.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;
    }

    // ---- plain moves ----

    private static void collectPlain(Piece[] board, int from, Piece piece, List<DraughtsMove> out) {
        for (Direction d : DIRECTIONS) {
            if (!piece.isKing() && !isForward(piece.colour(), d)) {
                continue;
            }
            for (int to : RAYS[from][d.ordinal()]) {
                if (board[to] != null) {
                    break;
                }
                boolean promotes = !piece.isKing() && BoardGeometry.isPromotionSquare(piece.colour(), to);
                out.add(new DraughtsMove(from, to, List.of(to), List.of(), promotes));
                if (!piece.isKing()) {
                    break;
                }
            }
        }
    }

    private static boolean isForward(Colour colour, Direction d) {
        boolean north = d == Direction.NORTH_WEST || d == Direction.NORTH_EAST;
        return colour == Colour.WHITE ? north : !north;
    }

    // ---- captures ----

    private static void collectCaptures(Piece[] original, int from, Piece piece, List<DraughtsMove> out) {
        Piece[] board = original.clone();
        board[from] = null; // the start square is free during the rafle
        new Rafle(board, from, piece, new boolean[BoardGeometry.SQUARES + 1], out).extend(from, new ArrayList<>(),
                new ArrayList<>());
    }

    /** Depth-first search of the capture sequences of one piece. */
    private static final class Rafle {
        private final Piece[] board;
        private final int from;
        private final Piece piece;
        private final boolean[] captured;
        private final List<DraughtsMove> out;

        Rafle(Piece[] board, int from, Piece piece, boolean[] captured, List<DraughtsMove> out) {
            this.board = board;
            this.from = from;
            this.piece = piece;
            this.captured = captured;
            this.out = out;
        }

        void extend(int at, List<Integer> path, List<Integer> captures) {
            boolean jumped = false;
            for (Direction d : DIRECTIONS) {
                int[] ray = RAYS[at][d.ordinal()];
                int victimIndex = findVictim(ray);
                if (victimIndex < 0) {
                    continue;
                }
                int victim = ray[victimIndex];
                int last = piece.isKing() ? ray.length : victimIndex + 2;
                for (int i = victimIndex + 1; i < last && i < ray.length && board[ray[i]] == null; i++) {
                    jumped = true;
                    captured[victim] = true;
                    path.add(ray[i]);
                    captures.add(victim);
                    extend(ray[i], path, captures);
                    captures.remove(captures.size() - 1);
                    path.remove(path.size() - 1);
                    captured[victim] = false;
                }
            }
            if (!jumped && !captures.isEmpty()) {
                int to = path.get(path.size() - 1);
                boolean promotes = !piece.isKing() && BoardGeometry.isPromotionSquare(piece.colour(), to);
                out.add(new DraughtsMove(from, to, path, captures, promotes));
            }
        }

        /**
         * The index in {@code ray} of the capturable opponent piece in that direction, or -1. A man looks at the
         * adjacent square only; a king skips free squares. A captured piece is an obstacle, not a victim.
         */
        private int findVictim(int[] ray) {
            for (int i = 0; i < ray.length; i++) {
                int square = ray[i];
                Piece p = board[square];
                if (p == null) {
                    if (piece.isKing()) {
                        continue;
                    }
                    return -1;
                }
                boolean victim = p.colour() != piece.colour() && !captured[square];
                return victim && i + 1 < ray.length ? i : -1;
            }
            return -1;
        }
    }

    /** Keeps the sequences with the largest capture count, one per (from, to, captured set), in a stable order. */
    private static List<DraughtsMove> majority(List<DraughtsMove> captures) {
        int best = captures.stream().mapToInt(m -> m.captures().size()).max().orElse(0);
        Set<String> seen = new HashSet<>();
        List<DraughtsMove> result = new ArrayList<>();
        for (DraughtsMove move : captures) {
            if (move.captures().size() == best
                    && seen.add(move.from() + ">" + move.to() + ">" + sortedCaptures(move))) {
                result.add(move);
            }
        }
        result.sort(ORDER);
        return result;
    }

    private static List<Integer> sortedCaptures(DraughtsMove move) {
        return move.captures().stream().sorted().toList();
    }

    private static int compareLists(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int c = Integer.compare(a.get(i), b.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
