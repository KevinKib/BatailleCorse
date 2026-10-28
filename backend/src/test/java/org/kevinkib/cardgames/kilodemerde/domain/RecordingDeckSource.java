package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

/** Test deck source: keeps every order it is given (no shuffling) and records the pile shuffles it was asked for. */
final class RecordingDeckSource implements DeckSource {

    final List<List<Card>> pileShuffles = new ArrayList<>();
    final List<List<Card>> handDeckShuffles = new ArrayList<>();

    @Override
    public List<Card> shuffleHandDeck(List<Card> handDeck) {
        handDeckShuffles.add(List.copyOf(handDeck));
        return List.copyOf(handDeck);
    }

    @Override
    public List<Card> shufflePile(List<Card> pile) {
        pileShuffles.add(List.copyOf(pile));
        return List.copyOf(pile);
    }
}
