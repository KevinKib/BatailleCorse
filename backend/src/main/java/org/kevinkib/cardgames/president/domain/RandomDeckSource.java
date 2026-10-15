package org.kevinkib.cardgames.president.domain;

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
import java.util.Random;

/** The default source: freshly shuffled French decks, reproducible from the seed. */
public class RandomDeckSource implements DeckSource {

    @Override
    public List<Card> deck(GameId id, int round, long seed, DeckSpec spec) {
        DeckType type = spec.jokers() ? DeckType.FRENCH_WITH_JOKERS : DeckType.FRENCH;
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < spec.decks(); i++) {
            Deck deck = new CardsService().createDeck(type, new DeckCreationOptions(Visibility.HIDDEN, seed));
            while (deck.getSize() > 0) {
                cards.add(deck.draw());
            }
        }
        if (spec.decks() > 1) {
            Collections.shuffle(cards, new Random(seed));
        }
        return cards;
    }
}
