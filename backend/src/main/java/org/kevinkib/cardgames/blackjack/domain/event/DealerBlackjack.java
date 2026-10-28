package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cards.domain.Card;

/** The dealer's peek found a blackjack: the hole card is shown and the hand is settled at once. */
public record DealerBlackjack(Card holeCard) implements BlackjackEvent {
}
