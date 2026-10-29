package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/**
 * The seat doubled its hand {@code handIndex}: the bet doubled, it took exactly this card and stands.
 *
 * @param value the hand total after the card
 */
public record Doubled(PlayerId seat, int handIndex, Card card, int value) implements BlackjackEvent {
}
