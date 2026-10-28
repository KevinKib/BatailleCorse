package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A source whose next deal can be fixed per game, for tests and the e2e seam that need a known game. A fixed deal is
 * used once (the hands at the next hand-deck shuffle, the pile at the next pile shuffle), then the delegate takes
 * over again. Fixed hands come first in seat order and the rest of the hand deck follows in the delegate's order;
 * a fixed pile is used exactly as given (a one-card pile makes a one-round game).
 */
public class ScriptedDeckSource implements DeckSource {

    private final DeckSource delegate;
    private final Map<GameId, List<Card>> fixedHands = new ConcurrentHashMap<>();
    private final Map<GameId, List<Card>> fixedPiles = new ConcurrentHashMap<>();

    public ScriptedDeckSource(DeckSource delegate) {
        this.delegate = delegate;
    }

    /**
     * Fixes the next deal of a game.
     *
     * @param hands the leading hands, in seat order (empty: nothing fixed)
     * @param pile  the kilo pile, top first (empty: nothing fixed)
     * @throws IllegalArgumentException for a card fixed twice
     */
    public void fixDeal(GameId id, List<List<Card>> hands, List<Card> pile) {
        List<Card> leading = hands.stream().flatMap(List::stream).toList();
        List<Card> all = new ArrayList<>(leading);
        all.addAll(pile);
        if (new HashSet<>(all).size() != all.size()) {
            throw new IllegalArgumentException("A card cannot be fixed twice");
        }
        fixedHands.put(id, leading);
        fixedPiles.put(id, List.copyOf(pile));
    }

    /** Forgets everything fixed for a game. */
    public void clear(GameId id) {
        fixedHands.remove(id);
        fixedPiles.remove(id);
    }

    @Override
    public List<Card> shuffleHandDeck(List<Card> handDeck) {
        return delegate.shuffleHandDeck(handDeck);
    }

    @Override
    public List<Card> shufflePile(List<Card> pile) {
        return delegate.shufflePile(pile);
    }

    @Override
    public List<Card> shuffleHandDeck(GameId id, List<Card> handDeck) {
        List<Card> leading = fixedHands.remove(id);
        List<Card> random = delegate.shuffleHandDeck(handDeck);
        if (leading == null || leading.isEmpty()) {
            return random;
        }
        List<Card> rest = new ArrayList<>(random);
        for (Card card : leading) {
            if (!rest.remove(card)) {
                throw new IllegalArgumentException("Card " + card + " is not in the hand deck");
            }
        }
        List<Card> deck = new ArrayList<>(leading);
        deck.addAll(rest);
        return deck;
    }

    @Override
    public List<Card> shufflePile(GameId id, List<Card> pile) {
        List<Card> fixed = fixedPiles.remove(id);
        List<Card> random = delegate.shufflePile(pile);
        if (fixed == null || fixed.isEmpty()) {
            return random;
        }
        if (!new HashSet<>(pile).containsAll(fixed)) {
            throw new IllegalArgumentException("A fixed pile card is not in the kilo pile");
        }
        return fixed;
    }
}
