package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Map;

/** What a hand wins or loses against the dealer (spec section 3.8), and which seats made the best hand. */
public final class Settlement {

    private Settlement() {
    }

    /**
     * The net gain of a hand: positive when it wins, negative when it loses, 0 for a push. A blackjack pays 3:2
     * rounded down to a whole point; every other win pays 1:1.
     *
     * @param hand   the player's hand, with its bet (doubled if it doubled)
     * @param dealer what the dealer's cards are worth
     */
    public static int net(PlayerHand hand, HandValue dealer) {
        HandValue player = hand.value();
        int bet = hand.bet();
        if (player.bust()) {
            return -bet;
        }
        if (player.blackjack()) {
            return dealer.blackjack() ? 0 : bet * 3 / 2;
        }
        if (dealer.blackjack()) {
            return -bet;
        }
        if (dealer.bust() || player.total() > dealer.total()) {
            return bet;
        }
        return player.total() == dealer.total() ? 0 : -bet;
    }

    /** The seat(s) with the largest positive total net, in the order of {@code nets}; none when nobody gained. */
    public static List<PlayerId> bestSeats(Map<PlayerId, Integer> nets) {
        int best = nets.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (best <= 0) {
            return List.of();
        }
        return nets.entrySet().stream().filter(entry -> entry.getValue() == best).map(Map.Entry::getKey).toList();
    }
}
