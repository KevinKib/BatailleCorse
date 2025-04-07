package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Hand;
import org.kevinkib.cards.domain.NoCardsException;

import java.util.List;
import java.util.Objects;

public class Player {

    private final Integer id;
    private final Hand hand;

    public Player(Integer id, Hand hand) {
        this.id = id;
        this.hand = hand;
    }

    public void addCards(List<Card> cards) {
        hand.add(cards);
    }

    public Card removeCardOnTop() throws NoCardsException {
        return hand.playCardOnTop();
    }

    public Card getCardOnTop() {
        return hand.getCardOnTop();
    }

    public Integer getId() {
        return id;
    }

    public Hand getHand() {
        return hand;
    }

    public int getHandSize() {
        return hand.getSize();
    }

    public boolean hasAnyCards() {
        return hand.hasAnyCards();
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
