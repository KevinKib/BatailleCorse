package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A source whose first cards can be chosen per game and per hand, for tests and the e2e seam that need a known game.
 * A scripted hand deals its prefix first (in the deal order of the spec, then the hit, split and dealer cards in the
 * order they are drawn) and then the rest of the six decks in a fixed order; a hand without a script comes from the
 * delegate. A script stays until {@link #clear(GameId)}.
 */
public class ScriptedShoeSource implements ShoeSource {

    private final ShoeSource delegate;
    private final Map<GameId, Map<Integer, List<Card>>> prefixes = new ConcurrentHashMap<>();

    public ScriptedShoeSource(ShoeSource delegate) {
        this.delegate = delegate;
    }

    /**
     * Scripts the first cards of one hand of one game.
     *
     * @param handNumber the hand, from 1
     * @param prefix     the cards, the first dealt first; a card may repeat as often as the six decks hold it
     * @throws IllegalArgumentException for a hand below 1 or more than 312 cards
     */
    public void fix(GameId id, int handNumber, List<Card> prefix) {
        if (handNumber < 1) {
            throw new IllegalArgumentException("Hands start at 1, not " + handNumber);
        }
        if (prefix.size() > Shoe.sixDecks().size()) {
            throw new IllegalArgumentException("A shoe holds 312 cards, not " + prefix.size());
        }
        prefixes.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(handNumber, List.copyOf(prefix));
    }

    /** Forgets every script of a game. */
    public void clear(GameId id) {
        prefixes.remove(id);
    }

    @Override
    public List<Card> shuffledShoe(GameId id, int handNumber) {
        List<Card> prefix = prefixes.getOrDefault(id, Map.of()).get(handNumber);
        if (prefix == null) {
            return delegate.shuffledShoe(id, handNumber);
        }
        List<Card> rest = new ArrayList<>(Shoe.sixDecks());
        for (Card card : prefix) {
            if (!rest.remove(card)) {
                throw new IllegalArgumentException("Card " + card + " is not available in the shoe");
            }
        }
        List<Card> shoe = new ArrayList<>(prefix);
        shoe.addAll(rest);
        return shoe;
    }
}
