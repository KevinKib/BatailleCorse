package org.kevinkib.cardgames.bullshit.domain.pile;

import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

public class DiscardPile {

    private final List<Card> cards = new ArrayList<>();

    public void add(List<Card> newCards) {
        cards.addAll(newCards);
    }

    public List<Card> takeAll() {
        List<Card> taken = new ArrayList<>(cards);
        cards.clear();
        return taken;
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
}
