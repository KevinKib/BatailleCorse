package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The default source: real randomness for every deal and every pile shuffle. */
public class RandomDeckSource implements DeckSource {

    @Override
    public List<Card> shuffleHandDeck(List<Card> handDeck) {
        return shuffled(handDeck);
    }

    @Override
    public List<Card> shufflePile(List<Card> pile) {
        return shuffled(pile);
    }

    private static List<Card> shuffled(List<Card> cards) {
        List<Card> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled;
    }
}
