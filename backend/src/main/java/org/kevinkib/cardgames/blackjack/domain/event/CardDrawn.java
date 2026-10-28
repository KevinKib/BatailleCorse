package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/** A hit: the card went to the seat's hand {@code handIndex} (0 or 1), whose total is now {@code value}. */
public record CardDrawn(PlayerId seat, int handIndex, Card card, int value) implements BlackjackEvent {
}
