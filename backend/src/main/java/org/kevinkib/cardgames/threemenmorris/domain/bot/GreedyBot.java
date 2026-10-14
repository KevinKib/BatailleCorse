package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Level {@code greedy}: plays a decision that wins at once (a line, or a blocked opponent) if it has one;
 * else a decision after which the opponent has no winning reply, which blocks a threatened line (a
 * placement on the missing point, or a move that leaves the opponent no line next turn); else any decision.
 * It looks one reply ahead and no further.
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
        List<MorrisDecision> safe = legal.stream().filter(decision -> opponentCannotWinAfter(position, decision)).toList();
        return pick(safe.isEmpty() ? legal : safe);
    }

    private static boolean opponentCannotWinAfter(MorrisPosition position, MorrisDecision decision) {
        MorrisPosition next = ((MorrisTransitions.Outcome.Next) MorrisTransitions.apply(position, decision)).position();
        return MorrisTransitions.legal(next).stream()
                .noneMatch(reply -> MorrisTransitions.apply(next, reply) instanceof MorrisTransitions.Outcome.Win);
    }
}
