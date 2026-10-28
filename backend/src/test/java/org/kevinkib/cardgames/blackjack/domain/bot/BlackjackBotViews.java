package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-built {@link BlackjackBotView}s for the bot tests. */
final class BlackjackBotViews {

    static final PlayerId ME = new PlayerId(0);

    private BlackjackBotViews() {
    }

    /** A betting view; {@code stacks} lists my stack first, then the others'. */
    static BlackjackBotView betting(int handsLeft, BlackjackOptions.MaxBet maxBet, int... stacks) {
        Map<PlayerId, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < stacks.length; i++) {
            map.put(new PlayerId(i), stacks[i]);
        }
        BlackjackOptions options = new BlackjackOptions(BlackjackOptions.Hands.TEN, maxBet);
        int cap = Math.min(maxBet.amount(), stacks[0]);
        return new BlackjackBotView(ME, 1, 1, handsLeft, Phase.BETTING, 0, List.of(), 0, List.of(Action.BET),
                BlackjackOptions.MIN_BET, cap, map, ME, stacks.length == 1, options);
    }

    /** A playing view with one hand of the given bet; {@code stacks} lists my stack first. */
    static BlackjackBotView playing(int handsLeft, BlackjackBotView.Hand hand, int up, List<Action> actions,
                                    int... stacks) {
        Map<PlayerId, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < stacks.length; i++) {
            map.put(new PlayerId(i), stacks[i]);
        }
        return new BlackjackBotView(ME, 1, 1, handsLeft, Phase.PLAYING, 0, List.of(hand), up, actions, 0, 0, map,
                ME, stacks.length == 1, BlackjackOptions.defaults());
    }

    static BlackjackBotView.Hand hand(int total, boolean soft, boolean pair, int bet) {
        return new BlackjackBotView.Hand(total, soft, pair, bet, false);
    }
}
