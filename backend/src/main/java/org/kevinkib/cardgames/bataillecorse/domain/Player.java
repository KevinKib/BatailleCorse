package org.kevinkib.cardgames.bataillecorse.domain;
import org.kevinkib.cardgames.game.PlayerId;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.hand.Hand;
import org.kevinkib.cards.domain.hand.NoCardsException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record Player(PlayerId id, Hand hand) {

    public Player(Integer id, Hand hand) {
        this(new PlayerId(id), hand);
    }

    public void addCardsFromPile(List<Card> cards) {
        Collections.reverse(cards);
        hand.add(cards);
    }

    public Card removeCardOnTop() throws NoCardsException {
        return hand.playCardOnTop();
    }

    public Card getCardOnTop() {
        return hand.getCardOnTop();
    }

    public int getHandSize() {
        return hand.getSize();
    }

    public List<Card> getCards() {
        return hand.getCards();
    }

    public boolean hasAnyCards() {
        return hand.hasAnyCards();
    }

    public boolean isEliminated() {
        return !hand.hasAnyCards();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
