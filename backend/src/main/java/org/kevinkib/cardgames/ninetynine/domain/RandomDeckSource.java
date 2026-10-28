package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Visibility;
import org.kevinkib.cards.domain.deck.Deck;
import org.kevinkib.cards.domain.deck.DeckCreationOptions;
import org.kevinkib.cards.domain.deck.DeckType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The default source: real randomness for every deal, reshuffle and first seat. */
public class RandomDeckSource implements DeckSource {

    @Override
    public List<Card> shuffledDeck(GameId id, int round) {
        Deck deck = new CardsService().createDeck(DeckType.FRENCH,
                new DeckCreationOptions(Visibility.HIDDEN, ThreadLocalRandom.current().nextLong()));
        List<Card> cards = new ArrayList<>();
        while (deck.getSize() > 0) {
            cards.add(deck.draw());
        }
        return cards;
    }

    @Override
    public List<Card> reshuffle(GameId id, List<Card> cards) {
        List<Card> shuffled = new ArrayList<>(cards);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled;
    }

    @Override
    public int firstSeat(GameId id, int seats) {
        return ThreadLocalRandom.current().nextInt(seats);
    }
}
