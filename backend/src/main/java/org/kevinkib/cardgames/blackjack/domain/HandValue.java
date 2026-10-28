package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/**
 * What a hand of cards is worth in Blackjack: the best total, whether an Ace still counts 11 (soft), a blackjack
 * (an Ace and a 10-value card as the first two cards, never after a split) and a bust. The only place that counts
 * cards: the DTO and the bots use it, the client never counts.
 */
public record HandValue(int total, boolean soft, boolean blackjack, boolean bust) {

    /**
     * @param cards     the cards of the hand, in the order they came
     * @param fromSplit whether the hand comes from a split (a 21 of two cards is then a plain 21)
     * @throws IllegalArgumentException on a card that has no Blackjack value (a Joker)
     */
    public static HandValue of(List<Card> cards, boolean fromSplit) {
        int sum = 0;
        boolean hasAce = false;
        for (Card card : cards) {
            if (!(card.getRank() instanceof FrenchRank rank) || rank.getValue() == null) {
                throw new IllegalArgumentException("No Blackjack value for " + card);
            }
            sum += rank.getValue();
            hasAce |= rank == FrenchRank.ACE;
        }
        boolean soft = hasAce && sum + 10 <= 21;
        int total = soft ? sum + 10 : sum;
        boolean blackjack = !fromSplit && cards.size() == 2 && total == 21;
        return new HandValue(total, soft, blackjack, total > 21);
    }
}
