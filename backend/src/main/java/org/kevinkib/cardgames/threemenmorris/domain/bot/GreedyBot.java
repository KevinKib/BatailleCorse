package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Level {@code greedy}: plays a decision that wins at once (a line under WIN rules, a blocked opponent, the
 * opponent's last needed piece) if it has one; else a decision that closes a mill; else a decision after
 * which the opponent has no winning or mill-closing reply, which blocks a threatened line (a placement on
 * the missing point, or a move that leaves the opponent no line next turn); else any decision. After a
 * mill it takes a piece whose loss leaves the opponent no mill, else the piece with the most free
 * neighbours. It looks one reply ahead and no further.
 */
public final class GreedyBot extends AbstractMorrisBot {

    public GreedyBot(RandomGenerator random) {
        super(random);
    }

    @Override
    MorrisDecision play(MorrisPosition position, PlayerId seat) {
        List<MorrisDecision> legal = MorrisTransitions.legal(position);
        List<MorrisDecision> winning = legal.stream()
                .filter(decision -> MorrisTransitions.apply(position, decision) instanceof MorrisTransitions.Outcome.Win)
                .toList();
        if (!winning.isEmpty()) {
            return pick(winning);
        }
        if (position.removing()) {
            return pick(bestRemovals(position, legal));
        }
        List<MorrisDecision> mills = legal.stream()
                .filter(decision -> nextOf(position, decision).removing())
                .toList();
        if (!mills.isEmpty()) {
            return pick(mills);
        }
        List<MorrisDecision> safe = legal.stream().filter(decision -> opponentCannotScoreAfter(position, decision)).toList();
        return pick(safe.isEmpty() ? legal : safe);
    }

    /** The removals after which the opponent has no scoring reply (if any), then those taking the most mobile piece. */
    private static List<MorrisDecision> bestRemovals(MorrisPosition position, List<MorrisDecision> removals) {
        List<MorrisDecision> calm = removals.stream()
                .filter(decision -> opponentCannotScoreAfter(position, decision))
                .toList();
        List<MorrisDecision> candidates = calm.isEmpty() ? removals : calm;
        int bestFree = -1;
        List<MorrisDecision> best = new ArrayList<>();
        for (MorrisDecision decision : candidates) {
            int free = freeNeighbours(position, ((MorrisDecision.Remove) decision).point());
            if (free > bestFree) {
                bestFree = free;
                best.clear();
            }
            if (free == bestFree) {
                best.add(decision);
            }
        }
        return best;
    }

    private static int freeNeighbours(MorrisPosition position, int point) {
        return (int) position.rules().geometry().neighbours(point).stream()
                .filter(neighbour -> position.board().get(neighbour) == null)
                .count();
    }

    private static MorrisPosition nextOf(MorrisPosition position, MorrisDecision decision) {
        return ((MorrisTransitions.Outcome.Next) MorrisTransitions.apply(position, decision)).position();
    }

    /** After the decision the opponent can neither win nor close a mill at once. */
    private static boolean opponentCannotScoreAfter(MorrisPosition position, MorrisDecision decision) {
        MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
        if (!(outcome instanceof MorrisTransitions.Outcome.Next afterDecision)) {
            return true;
        }
        MorrisPosition next = afterDecision.position();
        return MorrisTransitions.legal(next).stream().noneMatch(reply -> {
            MorrisTransitions.Outcome answer = MorrisTransitions.apply(next, reply);
            return answer instanceof MorrisTransitions.Outcome.Win
                    || ((MorrisTransitions.Outcome.Next) answer).position().removing();
        });
    }
}
