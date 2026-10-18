package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of the game as a pure function of a {@link MorrisPosition}: which placements and moves are
 * legal, and what a decision leads to. The bots and the solver read the game through this class; a test
 * replays random games against the aggregate to prove the two statements of the rules agree.
 *
 * <p>It models the placement and movement phases, a line and a blocked opponent. It does not model the
 * draw by repetition or by agreement, which depend on the history of the game, not on the position.
 */
public final class MorrisTransitions {

    /** What a decision leads to. */
    public sealed interface Outcome {
        /** The mover aligned three pieces, or left the opponent no move: the game is over. */
        record Win(int seat) implements Outcome {
        }

        /** The game goes on from this position. */
        record Next(MorrisPosition position) implements Outcome {
        }
    }

    private MorrisTransitions() {
    }

    public static boolean isPlacement(MorrisPosition position) {
        return position.inHand().get(0) > 0 || position.inHand().get(1) > 0;
    }

    /** The placements or moves of the seat to move, in point order. */
    public static List<MorrisDecision> legal(MorrisPosition position) {
        List<MorrisDecision> decisions = new ArrayList<>();
        Geometry geometry = position.rules().geometry();
        if (isPlacement(position)) {
            for (int point = 0; point < geometry.points(); point++) {
                if (position.board().get(point) == null) {
                    decisions.add(new MorrisDecision.Place(point));
                }
            }
        } else {
            for (int from = 0; from < geometry.points(); from++) {
                if (Integer.valueOf(position.toMove()).equals(position.board().get(from))) {
                    for (int to : geometry.neighbours(from)) {
                        if (position.board().get(to) == null) {
                            decisions.add(new MorrisDecision.Step(from, to));
                        }
                    }
                }
            }
        }
        return decisions;
    }

    /** What the placement or move leads to; the decision must be one of {@link #legal}. */
    public static Outcome apply(MorrisPosition position, MorrisDecision decision) {
        int mover = position.toMove();
        List<Integer> board = new ArrayList<>(position.board());
        List<Integer> inHand = new ArrayList<>(position.inHand());
        if (decision instanceof MorrisDecision.Place place) {
            board.set(place.point(), mover);
            inHand.set(mover, inHand.get(mover) - 1);
        } else if (decision instanceof MorrisDecision.Step step) {
            board.set(step.from(), null);
            board.set(step.to(), mover);
        } else {
            throw new IllegalArgumentException("Not a placement or a move: " + decision);
        }
        if (hasLine(position.rules(), board, mover)) {
            return new Outcome.Win(mover);
        }
        MorrisPosition next = new MorrisPosition(position.rules(), board, inHand, 1 - mover);
        if (!isPlacement(next) && legal(next).isEmpty()) {
            return new Outcome.Win(mover);
        }
        return new Outcome.Next(next);
    }

    private static boolean hasLine(MorrisRules rules, List<Integer> board, int seat) {
        return rules.geometry().lines().stream()
                .anyMatch(line -> line.stream().allMatch(point -> Integer.valueOf(seat).equals(board.get(point))));
    }
}
