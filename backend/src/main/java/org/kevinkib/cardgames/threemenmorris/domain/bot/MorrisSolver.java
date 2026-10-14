package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game of one variant, solved by retrograde analysis. Every position reachable from the opening is
 * listed (a few thousand), then values are propagated backwards from the endings: a position is a WIN for
 * the seat to move if one of its decisions wins at once or leads to a LOSS for the opponent, a LOSS if
 * every decision leads to a WIN for the opponent; whatever stays unresolved is a DRAW (the movement phase
 * has cycles, so a plain minimax would not terminate). {@link Solution#depth()} is the number of plies
 * to the end under best play: the shortest for a win, the longest for a loss.
 *
 * <p>Positions are stored from the point of view of the seat to move, so the seat that is white does not
 * matter. The draw by repetition is not modelled: a won position can always be won without repeating.
 */
public final class MorrisSolver {

    public enum Outcome { WIN, LOSS, DRAW }

    /** The value of a position for the seat to move. A draw has no depth (0). */
    public record Solution(Outcome outcome, int depth) {
    }

    private static final Map<MorrisRules, MorrisSolver> CACHE = new ConcurrentHashMap<>();

    /** The solver of these rules, computed on first use and then shared. */
    public static MorrisSolver forRules(MorrisRules rules) {
        return CACHE.computeIfAbsent(rules, MorrisSolver::new);
    }

    private final Map<MorrisPosition, Solution> solutions = new HashMap<>();

    /** Solves the variant from scratch; prefer {@link #forRules}, which caches. */
    public MorrisSolver(MorrisRules rules) {
        List<MorrisPosition> positions = new ArrayList<>();
        Map<MorrisPosition, Integer> indexOf = new HashMap<>();
        MorrisPosition opening = normalised(new MorrisPosition(rules,
                java.util.Collections.nCopies(rules.geometry().points(), null),
                List.of(rules.piecesPerPlayer(), rules.piecesPerPlayer()), 0));
        indexOf.put(opening, 0);
        positions.add(opening);

        // successors.get(i): indices of the positions reached; winsAtOnce: a decision that ends the game.
        List<int[]> successors = new ArrayList<>();
        List<Boolean> winsAtOnce = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            List<Integer> reached = new ArrayList<>();
            boolean wins = false;
            for (MorrisDecision decision : MorrisTransitions.legal(positions.get(i))) {
                MorrisTransitions.Outcome outcome = MorrisTransitions.apply(positions.get(i), decision);
                if (outcome instanceof MorrisTransitions.Outcome.Next next) {
                    MorrisPosition target = normalised(next.position());
                    Integer index = indexOf.get(target);
                    if (index == null) {
                        index = positions.size();
                        indexOf.put(target, index);
                        positions.add(target);
                    }
                    reached.add(index);
                } else {
                    wins = true;
                }
            }
            successors.add(reached.stream().mapToInt(Integer::intValue).toArray());
            winsAtOnce.add(wins);
        }

        int size = positions.size();
        List<List<Integer>> predecessors = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            predecessors.add(new ArrayList<>());
        }
        int[] undecided = new int[size];
        for (int i = 0; i < size; i++) {
            undecided[i] = successors.get(i).length;
            for (int target : successors.get(i)) {
                predecessors.get(target).add(i);
            }
        }

        Outcome[] outcome = new Outcome[size];
        int[] depth = new int[size];
        Deque<Integer> queue = new ArrayDeque<>();
        // Seeds, shallowest first: a position with no decision at all loses (depth 0), then wins in one ply.
        for (int i = 0; i < size; i++) {
            if (successors.get(i).length == 0 && !winsAtOnce.get(i)) {
                outcome[i] = Outcome.LOSS;
                queue.add(i);
            }
        }
        for (int i = 0; i < size; i++) {
            if (winsAtOnce.get(i)) {
                outcome[i] = Outcome.WIN;
                depth[i] = 1;
                queue.add(i);
            }
        }
        // The queue is processed by non-decreasing depth, so the first value found is the best one.
        while (!queue.isEmpty()) {
            int resolved = queue.poll();
            for (int predecessor : predecessors.get(resolved)) {
                if (outcome[predecessor] != null) {
                    continue;
                }
                if (outcome[resolved] == Outcome.LOSS) {
                    outcome[predecessor] = Outcome.WIN;
                    depth[predecessor] = depth[resolved] + 1;
                    queue.add(predecessor);
                } else if (--undecided[predecessor] == 0) {
                    outcome[predecessor] = Outcome.LOSS;
                    depth[predecessor] = depth[resolved] + 1;
                    queue.add(predecessor);
                }
            }
        }

        for (int i = 0; i < size; i++) {
            solutions.put(positions.get(i), outcome[i] == null
                    ? new Solution(Outcome.DRAW, 0)
                    : new Solution(outcome[i], depth[i]));
        }
    }

    /** How many positions are reachable (counted from the seat to move's point of view). */
    public int size() {
        return solutions.size();
    }

    public boolean contains(MorrisPosition position) {
        return solutions.containsKey(normalised(position));
    }

    public Optional<Solution> find(MorrisPosition position) {
        return Optional.ofNullable(solutions.get(normalised(position)));
    }

    /** The value of the position for its seat to move. */
    public Solution solve(MorrisPosition position) {
        return find(position).orElseThrow(() ->
                new IllegalArgumentException("Not a reachable position of these rules: " + position));
    }

    /** The same position with the seat to move relabelled 0, so both colours share their solutions. */
    private static MorrisPosition normalised(MorrisPosition position) {
        if (position.toMove() == 0) {
            return position;
        }
        List<Integer> board = position.board().stream().map(owner -> owner == null ? null : 1 - owner).toList();
        return new MorrisPosition(position.rules(), board,
                List.of(position.inHand().get(1), position.inHand().get(0)), 0);
    }
}
