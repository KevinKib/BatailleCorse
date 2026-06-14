package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.hand.CannotPlayNonPossessedCardException;
import org.kevinkib.cards.domain.hand.Hand;

import java.util.List;
import java.util.Objects;

public record Player(PlayerId id, Hand hand) {

    public Player(Integer id, Hand hand) {
        this(new PlayerId(id), hand);
    }

    public boolean possessesAll(List<Card> cards) {
        return cards.stream().allMatch(hand::possesses);
    }

    public void discard(List<Card> cards) {
        for (Card card : cards) {
            try {
                hand.play(card);
            } catch (CannotPlayNonPossessedCardException e) {
                throw new IllegalStateException("Card not in hand after possession check: " + card, e);
            }
        }
    }

    public void addCards(List<Card> cards) {
        hand.add(cards);
    }

    public List<Card> getCards() {
        return hand.getCards();
    }

    public int handSize() {
        return hand.getSize();
    }

    public boolean hasAnyCards() {
        return hand.hasAnyCards();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Player) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
