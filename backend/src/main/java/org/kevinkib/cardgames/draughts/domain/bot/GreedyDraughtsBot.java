package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry.Direction;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.Piece;

/**
 * Medium: two plies by material. For each move, the opponent's best reply is assumed (the one that leaves the
 * bot with the worst balance); the moves with the best resulting balance are kept, and among them a promotion
 * wins over advancing a man that stays defended, which wins over anything else; the rest is random.
 */
public class GreedyDraughtsBot extends AbstractDraughtsBot {

    /** The score of a move after which the opponent has no legal move: the bot has won. */
    private static final int WON = 1_000;

    private final RandomGenerator random;

    public GreedyDraughtsBot(RandomGenerator random) {
        this.random = random;
    }

    @Override
    protected DraughtsMove choose(DraughtsPosition position, Colour me, List<DraughtsMove> moves) {
        int bestBalance = Integer.MIN_VALUE;
        int bestTaste = Integer.MIN_VALUE;
        List<DraughtsMove> best = new ArrayList<>();
        for (DraughtsMove move : moves) {
            DraughtsPosition after = generator.apply(position, move);
            int balance = worstBalanceAfterReply(after, me);
            int taste = taste(after, move, me);
            if (balance > bestBalance || (balance == bestBalance && taste > bestTaste)) {
                bestBalance = balance;
                bestTaste = taste;
                best.clear();
            }
            if (balance == bestBalance && taste == bestTaste) {
                best.add(move);
            }
        }
        return best.get(random.nextInt(best.size()));
    }

    private int worstBalanceAfterReply(DraughtsPosition after, Colour me) {
        List<DraughtsMove> replies = generator.legalMoves(after);
        if (replies.isEmpty()) {
            return WON;
        }
        int worst = Integer.MAX_VALUE;
        for (DraughtsMove reply : replies) {
            DraughtsPosition end = generator.apply(after, reply);
            worst = Math.min(worst, DraughtsMaterial.of(end, me) - DraughtsMaterial.of(end, me.opposite()));
        }
        return worst;
    }

    /** 2 for a promotion, 1 for a man that now has a friend behind it, 0 otherwise. */
    private static int taste(DraughtsPosition after, DraughtsMove move, Colour me) {
        if (move.promotes()) {
            return 2;
        }
        Piece moved = after.pieceAt(move.to());
        if (moved == null || moved.isKing()) {
            return 0;
        }
        Direction[] behind = me == Colour.WHITE
                ? new Direction[] {Direction.SOUTH_WEST, Direction.SOUTH_EAST}
                : new Direction[] {Direction.NORTH_WEST, Direction.NORTH_EAST};
        for (Direction direction : behind) {
            int square = BoardGeometry.neighbour(move.to(), direction);
            if (square != 0 && after.pieceAt(square) != null && after.pieceAt(square).colour() == me) {
                return 1;
            }
        }
        return 0;
    }
}
