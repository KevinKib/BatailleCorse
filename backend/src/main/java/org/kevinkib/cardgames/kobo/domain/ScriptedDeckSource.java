package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A source whose first cards can be chosen per game and round, for tests that need a known deal. The cards
 * fixed for a round come first in deal order; the rest of the 52 follow in the delegate's (random) order.
 * A game or round with nothing fixed is dealt by the delegate alone.
 */
public class ScriptedDeckSource implements DeckSource {

    private final DeckSource delegate;
    private final Map<GameId, Map<Integer, List<Card>>> fixed = new ConcurrentHashMap<>();

    public ScriptedDeckSource(DeckSource delegate) {
        this.delegate = delegate;
    }

    /**
     * Fixes the leading cards, in deal order, of one round of one game.
     *
     * @throws IllegalArgumentException for a round below 1, more than 52 cards or a repeated card
     */
    public void fix(GameId id, int round, List<Card> leadingCards) {
        if (round < 1) {
            throw new IllegalArgumentException("Rounds start at 1, not " + round);
        }
        if (leadingCards.size() > 52) {
            throw new IllegalArgumentException("A deck holds 52 cards, not " + leadingCards.size());
        }
        if (new HashSet<>(leadingCards).size() != leadingCards.size()) {
            throw new IllegalArgumentException("A card cannot be fixed twice");
        }
        fixed.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(round, List.copyOf(leadingCards));
    }

    /** Forgets everything fixed for a game. */
    public void clear(GameId id) {
        fixed.remove(id);
    }

    @Override
    public List<Card> deck(GameId id, int round, long seed) {
        List<Card> random = delegate.deck(id, round, seed);
        List<Card> leading = fixed.getOrDefault(id, Map.of()).get(round);
        if (leading == null) {
            return random;
        }
        List<Card> deck = new ArrayList<>(leading);
        random.stream().filter(c -> !leading.contains(c)).forEach(deck::add);
        return deck;
    }
}
