package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.List;

/** The cards of the hand in progress, dealt from the top. */
final class Shoe {

    /** Decks in a shoe. */
    static final int DECKS = 6;

    private static final List<FrenchRank> RANKS = List.of(
            FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE, FrenchRank.SIX, FrenchRank.SEVEN,
            FrenchRank.EIGHT, FrenchRank.NINE, FrenchRank.TEN, FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING,
            FrenchRank.ACE);

    private static final List<FrenchSuit> SUITS = List.of(
            FrenchSuit.CLUB, FrenchSuit.DIAMOND, FrenchSuit.HEART, FrenchSuit.SPADE);

    private final List<Card> cards;
    private int next;

    Shoe(List<Card> cards) {
        this.cards = List.copyOf(cards);
    }

    /** The 312 cards of six French decks (no jokers), one deck after the other, in a fixed order. */
    static List<Card> sixDecks() {
        List<Card> all = new ArrayList<>();
        for (int deck = 0; deck < DECKS; deck++) {
            for (FrenchSuit suit : SUITS) {
                for (FrenchRank rank : RANKS) {
                    all.add(new Card(rank, suit));
                }
            }
        }
        return all;
    }

    Card draw() {
        if (next >= cards.size()) {
            throw new IllegalStateException("The shoe is empty");
        }
        return cards.get(next++);
    }
}
