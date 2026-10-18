package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of the game as a pure function of a {@link MorrisPosition}: which placements, moves and
 * removals are legal, and what a decision leads to. The bots and the solver read the game through this
 * class; a test replays random games against the aggregate to prove the two statements of the rules agree.
 *
 * <p>Under {@code WIN} rules a line, or a blocked opponent, ends the game. Under {@code MILL} rules (Six
 * Men's Morris) a placement or a move that closes a line through its destination gives a position in which
 * the same seat must {@link MorrisDecision.Remove} an opponent piece; a player who falls to two pieces (on
 * the board and in hand) or who cannot move loses. A seat reduced to three pieces flies when the rules say
 * so. The draw by repetition, by agreement and by quiet plies depend on the history of the game, not on
 * the position, so they are not modelled here.
 */
public final class MorrisTransitions {

    /** What a decision leads to. */
    public sealed interface Outcome {
        /** The mover aligned three pieces (WIN rules), took the opponent's third last piece, or left the opponent no move: the game is over. */
        record Win(int seat) implements Outcome {
        }

        /** The game goes on from this position (with a pending removal after a mill). */
        record Next(MorrisPosition position) implements Outcome {
        }
    }

    /** A player with fewer pieces than this (board plus hand) has lost, under mill rules. */
    private static final int MIN_PIECES = 3;

    private MorrisTransitions() {
    }

    public static boolean isPlacement(MorrisPosition position) {
        return position.inHand().get(0) > 0 || position.inHand().get(1) > 0;
    }

    /** The pieces a seat still has: on the board plus in hand. */
    public static int piecesLeft(MorrisPosition position, int seat) {
        int onBoard = (int) position.board().stream().filter(owner -> owner != null && owner == seat).count();
        return onBoard + position.inHand().get(seat);
    }

    /** The removals, placements or moves of the seat to move, in point order. */
    public static List<MorrisDecision> legal(MorrisPosition position) {
        if (position.removing()) {
            return removals(position);
        }
        List<MorrisDecision> decisions = new ArrayList<>();
        Geometry geometry = position.rules().geometry();
        if (isPlacement(position)) {
            for (int point = 0; point < geometry.points(); point++) {
                if (position.board().get(point) == null) {
                    decisions.add(new MorrisDecision.Place(point));
                }
            }
        } else {
            boolean flying = flies(position, position.toMove());
            for (int from = 0; from < geometry.points(); from++) {
                if (Integer.valueOf(position.toMove()).equals(position.board().get(from))) {
                    List<Integer> targets = flying ? allPoints(geometry) : geometry.neighbours(from);
                    for (int to : targets) {
                        if (position.board().get(to) == null) {
                            decisions.add(new MorrisDecision.Step(from, to));
                        }
                    }
                }
            }
        }
        return decisions;
    }

    /** What the decision leads to; it must be one of {@link #legal}. */
    public static Outcome apply(MorrisPosition position, MorrisDecision decision) {
        int mover = position.toMove();
        List<Integer> board = new ArrayList<>(position.board());
        List<Integer> inHand = new ArrayList<>(position.inHand());
        if (position.removing()) {
            if (!(decision instanceof MorrisDecision.Remove remove)) {
                throw new IllegalArgumentException("A removal is pending: " + decision);
            }
            board.set(remove.point(), null);
            return afterTurn(position, board, inHand, mover);
        }
        int destination;
        if (decision instanceof MorrisDecision.Place place) {
            board.set(place.point(), mover);
            inHand.set(mover, inHand.get(mover) - 1);
            destination = place.point();
        } else if (decision instanceof MorrisDecision.Step step) {
            board.set(step.from(), null);
            board.set(step.to(), mover);
            destination = step.to();
        } else {
            throw new IllegalArgumentException("Not a placement or a move: " + decision);
        }
        MorrisRules rules = position.rules();
        if (rules.lineRule() == MorrisRules.LineRule.WIN) {
            if (hasLine(rules, board, mover)) {
                return new Outcome.Win(mover);
            }
        } else if (closesMill(rules, board, destination, mover) && hasPieceOf(board, 1 - mover)) {
            return new Outcome.Next(new MorrisPosition(rules, board, inHand, mover, true));
        }
        return afterTurn(position, board, inHand, mover);
    }

    /** The turn is over: the opponent has lost if reduced below three pieces or if it cannot move. */
    private static Outcome afterTurn(MorrisPosition position, List<Integer> board, List<Integer> inHand, int mover) {
        MorrisRules rules = position.rules();
        MorrisPosition next = new MorrisPosition(rules, board, inHand, 1 - mover);
        if (rules.lineRule() == MorrisRules.LineRule.MILL && piecesLeft(next, 1 - mover) < MIN_PIECES) {
            return new Outcome.Win(mover);
        }
        if (!isPlacement(next) && legal(next).isEmpty()) {
            return new Outcome.Win(mover);
        }
        return new Outcome.Next(next);
    }

    /** The opponent pieces outside every mill, or all of them when each one is in a mill. */
    private static List<MorrisDecision> removals(MorrisPosition position) {
        int opponent = 1 - position.toMove();
        List<Integer> unprotected = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (int point = 0; point < position.board().size(); point++) {
            if (Integer.valueOf(opponent).equals(position.board().get(point))) {
                all.add(point);
                if (!inMill(position.rules(), position.board(), point, opponent)) {
                    unprotected.add(point);
                }
            }
        }
        List<MorrisDecision> decisions = new ArrayList<>();
        for (int point : unprotected.isEmpty() ? all : unprotected) {
            decisions.add(new MorrisDecision.Remove(point));
        }
        return decisions;
    }

    /** True when the seat is down to three pieces on the board with none in hand and the rules let it fly. */
    private static boolean flies(MorrisPosition position, int seat) {
        return position.rules().flying() == MorrisRules.FlyingRule.AT_THREE
                && position.inHand().get(seat) == 0
                && piecesLeft(position, seat) == MIN_PIECES;
    }

    private static List<Integer> allPoints(Geometry geometry) {
        List<Integer> points = new ArrayList<>();
        for (int point = 0; point < geometry.points(); point++) {
            points.add(point);
        }
        return points;
    }

    private static boolean hasPieceOf(List<Integer> board, int seat) {
        return board.contains(seat);
    }

    private static boolean hasLine(MorrisRules rules, List<Integer> board, int seat) {
        return rules.geometry().lines().stream()
                .anyMatch(line -> line.stream().allMatch(point -> Integer.valueOf(seat).equals(board.get(point))));
    }

    private static boolean closesMill(MorrisRules rules, List<Integer> board, int destination, int seat) {
        return rules.geometry().lines().stream()
                .filter(line -> line.contains(destination))
                .anyMatch(line -> line.stream().allMatch(point -> Integer.valueOf(seat).equals(board.get(point))));
    }

    private static boolean inMill(MorrisRules rules, List<Integer> board, int point, int seat) {
        return closesMill(rules, board, point, seat);
    }
}
