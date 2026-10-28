package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A source whose first cards, first seat and next reshuffle can be chosen per game, for tests that need a known
 * game. The cards fixed for a round come first in deal order, the rest of the deck follows in the delegate's
 * order. A fixed first seat and a fixed reshuffle are used once, then the delegate takes over again.
 */
public class ScriptedDeckSource implements DeckSource {

    private static final int DECK_SIZE = 52;

    private final DeckSource delegate;
    private final Map<GameId, Map<Integer, List<Card>>> fixedDecks = new ConcurrentHashMap<>();
    private final Map<GameId, Integer> fixedFirstSeats = new ConcurrentHashMap<>();
    private final Map<GameId, List<Card>> fixedReshuffles = new ConcurrentHashMap<>();

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
        if (leadingCards.size() > DECK_SIZE) {
            throw new IllegalArgumentException("A deck holds 52 cards, not " + leadingCards.size());
        }
        if (new HashSet<>(leadingCards).size() != leadingCards.size()) {
            throw new IllegalArgumentException("A card cannot be fixed twice");
        }
        fixedDecks.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(round, List.copyOf(leadingCards));
    }

    /** Fixes the seat (0-based) that starts round 1 of a game, once. */
    public void fixFirstSeat(GameId id, int seat) {
        fixedFirstSeats.put(id, seat);
    }

    /** Fixes the leading cards of the next reshuffle of a game, once; the other cards follow in random order. */
    public void fixReshuffle(GameId id, List<Card> leadingCards) {
        if (new HashSet<>(leadingCards).size() != leadingCards.size()) {
            throw new IllegalArgumentException("A card cannot be fixed twice");
        }
        fixedReshuffles.put(id, List.copyOf(leadingCards));
    }

    /** Forgets everything fixed for a game. */
    public void clear(GameId id) {
        fixedDecks.remove(id);
        fixedFirstSeats.remove(id);
        fixedReshuffles.remove(id);
    }

    @Override
    public List<Card> shuffledDeck(GameId id, int round) {
        List<Card> random = delegate.shuffledDeck(id, round);
        List<Card> leading = fixedDecks.getOrDefault(id, Map.of()).get(round);
        if (leading == null) {
            return random;
        }
        List<Card> rest = new ArrayList<>(random);
        for (Card card : leading) {
            if (!rest.remove(card)) {
                throw new IllegalArgumentException("Card " + card + " is not available in the deck");
            }
        }
        List<Card> deck = new ArrayList<>(leading);
        deck.addAll(rest);
        return deck;
    }

    @Override
    public List<Card> reshuffle(GameId id, List<Card> cards) {
        List<Card> leading = fixedReshuffles.remove(id);
        List<Card> shuffled = delegate.reshuffle(id, cards);
        if (leading == null) {
            return shuffled;
        }
        List<Card> rest = new ArrayList<>(shuffled);
        for (Card card : leading) {
            if (!rest.remove(card)) {
                throw new IllegalArgumentException("Card " + card + " is not in the pile to reshuffle");
            }
        }
        List<Card> result = new ArrayList<>(leading);
        result.addAll(rest);
        return result;
    }

    @Override
    public int firstSeat(GameId id, int seats) {
        Integer fixed = fixedFirstSeats.remove(id);
        return fixed != null ? fixed : delegate.firstSeat(id, seats);
    }
}
