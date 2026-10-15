package org.kevinkib.cardgames.president.domain;

/** What a deck is made of: how many 52-card decks, with or without the two jokers of each. */
public record DeckSpec(int decks, boolean jokers) {

    public DeckSpec {
        if (decks < 1) {
            throw new IllegalArgumentException("A deal needs at least one deck, not " + decks);
        }
    }

    /** One French deck of 52 cards, the only deck of the base game. */
    public static DeckSpec standard() {
        return new DeckSpec(1, false);
    }

    public int size() {
        return decks * (jokers ? 54 : 52);
    }
}
