package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Map;

/**
 * The {@code hard} level: basic strategy, but the bet follows the stacks of the tournament and the last hand
 * deviates to chase or protect first place (spec section 10.3). Others' bets are hidden, so "the leader" is the
 * richest other stack as shown; during play every stack has its bet already taken.
 */
public class TournamentBlackjackBot implements BlackjackBotStrategy {

    @Override
    public int decideBet(BlackjackBotView view) {
        BotChecks.requireBetting(view);
        int bestOther = bestOtherStack(view);
        if (view.solo() || bestOther < 0) {
            return BotChecks.clampBet(view, BlackjackBotTuning.FLAT_BET);
        }
        int lead = view.stacks().get(view.seat()) - bestOther;
        int maxBet = view.options().maxBet().amount();
        int cap = view.maxBet();
        int left = view.handsLeft();
        int bet;
        if (lead > (long) cap * left) {
            bet = 1;
        } else if (lead > 0 && left <= BlackjackBotTuning.MATCH_SECOND_HANDS_LEFT) {
            int secondCap = Math.min(maxBet, bestOther);
            bet = lead > 2 * secondCap ? 1 : Math.min(cap, secondCap);
        } else if (lead < 0 && left <= BlackjackBotTuning.ALL_IN_HANDS_LEFT) {
            bet = cap;
        } else if (lead < 0 && -lead > (long) maxBet * (left - 1)) {
            bet = cap;
        } else {
            int behind = Math.max(0, -lead);
            bet = BlackjackBotTuning.FLAT_BET
                    + BlackjackBotTuning.BEHIND_BONUS * (behind / BlackjackBotTuning.BEHIND_STEP);
        }
        return BotChecks.clampBet(view, bet);
    }

    @Override
    public Action decidePlay(BlackjackBotView view) {
        List<Action> actions = BotChecks.playable(view);
        BlackjackBotView.Hand hand = view.hands().get(view.activeHandIndex());
        if (view.handsLeft() == BlackjackBotTuning.LAST_HAND && !view.solo()) {
            Action deviation = lastHandDeviation(view, hand, actions);
            if (deviation != null) {
                return deviation;
            }
        }
        return BasicStrategy.decide(hand.total(), hand.soft(), hand.pair(), view.dealerUpValue(), actions);
    }

    private Action lastHandDeviation(BlackjackBotView view, BlackjackBotView.Hand hand, List<Action> actions) {
        int bestOther = bestOtherStack(view);
        if (bestOther < 0) {
            return null;
        }
        int mine = view.stacks().get(view.seat());
        if (mine > bestOther) {
            boolean weakDealer = view.dealerUpValue() <= BlackjackBotTuning.WEAK_UP_MAX;
            if (weakDealer && !hand.soft() && hand.total() >= BlackjackBotTuning.PROTECT_STAND_MIN
                    && actions.contains(Action.STAND)) {
                return Action.STAND;
            }
            return null;
        }
        if (hand.doubled()) {
            return null;
        }
        // The stack already holds the bet: a win ends at mine + 2 bets, a doubled win at mine + 3 bets.
        int bet = hand.bet();
        if (mine + 2 * bet > bestOther) {
            return null;
        }
        if (mine + 3 * bet > bestOther) {
            boolean aggressive = hand.soft() || hand.total() <= 11;
            return aggressive && actions.contains(Action.DOUBLE) ? Action.DOUBLE : null;
        }
        boolean stiff = !hand.soft() && hand.total() >= 12 && hand.total() <= BlackjackBotTuning.DESPERATE_HIT_MAX;
        return stiff && actions.contains(Action.HIT) ? Action.HIT : null;
    }

    /** The largest stack among the other live seats, or -1 when there is none. */
    private static int bestOtherStack(BlackjackBotView view) {
        int best = -1;
        for (Map.Entry<PlayerId, Integer> entry : view.stacks().entrySet()) {
            if (!entry.getKey().equals(view.seat())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }
}
