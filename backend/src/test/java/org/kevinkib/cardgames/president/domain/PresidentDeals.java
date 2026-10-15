package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * Complete 52-card decks in deal order with chosen cards at chosen seats. The rest of the deck is shuffled
 * with a fixed seed, so a test names only the cards it cares about and stays deterministic.
 */
final class PresidentDeals {

    private PresidentDeals() {
    }

    /**
     * @param players  seats 0 to players - 1, all active
     * @param dealer   the seat before the one that receives the first card
     * @param keyCards the cards a seat must hold, e.g. {@code Map.of(3, "2S 2H")}; they are dealt first
     */
    static List<Card> deck(int players, int dealer, Map<Integer, String> keyCards) {
        List<Card> rest = new ArrayList<>();
        for (FrenchRank rank : FrenchRank.values()) {
            if (rank == FrenchRank.JOKER) {
                continue;
            }
            for (FrenchSuit suit : new FrenchSuit[]{FrenchSuit.CLUB, FrenchSuit.DIAMOND, FrenchSuit.HEART,
                    FrenchSuit.SPADE}) {
                rest.add(new Card(rank, suit));
            }
        }
        List<List<Card>> keys = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            List<Card> held = keyCards.containsKey(seat) ? cards(keyCards.get(seat)) : List.of();
            rest.removeAll(held);
            keys.add(held);
        }
        Collections.shuffle(rest, new Random(42));
        List<Card> deck = new ArrayList<>();
        int[] dealt = new int[players];
        for (int i = 0; i < 52; i++) {
            int seat = (dealer + 1 + i) % players;
            List<Card> held = keys.get(seat);
            deck.add(dealt[seat] < held.size() ? held.get(dealt[seat]) : rest.remove(0));
            dealt[seat]++;
        }
        return deck;
    }
}
