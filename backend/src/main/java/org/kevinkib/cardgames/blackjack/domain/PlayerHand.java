package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * One hand of a seat: its cards, its bet (doubled once it doubled), where it comes from and its status. A seat holds
 * one hand, or two after a split. Immutable: the aggregate replaces it as the hand evolves.
 *
 * @param doubled    whether the hand doubled down
 * @param fromSplit  whether the hand comes from a split (a 21 of two cards is then a plain 21)
 * @param splitAces  whether the hand comes from split Aces (one card, no further action)
 */
public record PlayerHand(List<Card> cards, int bet, boolean doubled, boolean fromSplit, boolean splitAces,
                         Status status) {

    /**
     * {@code ACTIVE}: can still act; {@code STOOD}: finished with a total; {@code BUST}: over 21;
     * {@code BLACKJACK}: an Ace and a 10-value card dealt first; {@code WAITING}: the second hand of a split before
     * it is dealt its second card.
     */
    public enum Status { ACTIVE, STOOD, BUST, BLACKJACK, WAITING }

    public PlayerHand {
        cards = List.copyOf(cards);
    }

    /** What the cards are worth. */
    public HandValue value() {
        return HandValue.of(cards, fromSplit);
    }

    /** Whether the first two cards have the same Blackjack value (a King and a 10 do). */
    boolean isPair() {
        return cards.size() == 2
                && HandValue.of(List.of(cards.get(0)), false).total() == HandValue.of(List.of(cards.get(1)), false).total();
    }

    PlayerHand withCard(Card card) {
        List<Card> next = new ArrayList<>(cards);
        next.add(card);
        return new PlayerHand(next, bet, doubled, fromSplit, splitAces, status);
    }

    PlayerHand withStatus(Status next) {
        return new PlayerHand(cards, bet, doubled, fromSplit, splitAces, next);
    }

    /** The hand after doubling down: the bet doubles and the one card is taken. */
    PlayerHand doubledWith(Card card) {
        List<Card> next = new ArrayList<>(cards);
        next.add(card);
        return new PlayerHand(next, bet * 2, true, fromSplit, splitAces, status);
    }
}
