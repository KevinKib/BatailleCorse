package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;

import java.util.List;

/** The {@code medium} level: basic strategy and a flat bet. */
public class BasicStrategyBlackjackBot implements BlackjackBotStrategy {

    @Override
    public int decideBet(BlackjackBotView view) {
        BotChecks.requireBetting(view);
        return BotChecks.clampBet(view, BlackjackBotTuning.FLAT_BET);
    }

    @Override
    public Action decidePlay(BlackjackBotView view) {
        List<Action> actions = BotChecks.playable(view);
        BlackjackBotView.Hand hand = view.hands().get(view.activeHandIndex());
        return BasicStrategy.decide(hand.total(), hand.soft(), hand.pair(), view.dealerUpValue(), actions);
    }
}
