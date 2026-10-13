package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Visibility;
import org.kevinkib.cards.domain.deck.Deck;
import org.kevinkib.cards.domain.deck.DeckCreationOptions;
import org.kevinkib.cards.domain.deck.DeckType;

import java.util.ArrayList;
import java.util.List;

/** The default source: a freshly shuffled French deck, reproducible from the seed. */
public class RandomDeckSource implements DeckSource {

    @Override
    public List<Card> deck(GameId id, int round, long seed) {
        Deck deck = new CardsService().createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN, seed));
        List<Card> cards = new ArrayList<>();
        while (deck.getSize() > 0) {
            cards.add(deck.draw());
        }
        return cards;
    }
}
