package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;

import java.util.List;
import java.util.random.RandomGenerator;

/** The {@code easy} level: a uniformly random legal bet and a uniformly random available action. */
public class RandomBlackjackBot implements BlackjackBotStrategy {

    private final RandomGenerator random;

    public RandomBlackjackBot(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public int decideBet(BlackjackBotView view) {
        BotChecks.requireBetting(view);
        return view.minBet() + random.nextInt(view.maxBet() - view.minBet() + 1);
    }

    @Override
    public Action decidePlay(BlackjackBotView view) {
        List<Action> actions = BotChecks.playable(view);
        return actions.get(random.nextInt(actions.size()));
    }
}
