package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.Phase;

import java.util.List;

/** Shared guards of the strategies. */
final class BotChecks {

    private BotChecks() {
    }

    static void requireBetting(BlackjackBotView view) {
        if (view.phase() != Phase.BETTING || view.maxBet() < view.minBet() || view.maxBet() < 1) {
            throw new IllegalStateException("The seat cannot bet");
        }
    }

    /** The playing actions on offer (the bet is not a play), or fails when there are none. */
    static List<Action> playable(BlackjackBotView view) {
        List<Action> actions = view.availableActions().stream().filter(a -> a != Action.BET).toList();
        if (view.phase() != Phase.PLAYING || actions.isEmpty() || view.hands().isEmpty()) {
            throw new IllegalStateException("The seat has nothing to play");
        }
        return actions;
    }

    static int clampBet(BlackjackBotView view, int bet) {
        return Math.max(view.minBet(), Math.min(view.maxBet(), bet));
    }
}
