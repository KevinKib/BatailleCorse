package org.kevinkib.cardgames.bullshit.domain.deck;

import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Visibility;
import org.kevinkib.cards.domain.deck.Deck;
import org.kevinkib.cards.domain.deck.DeckCreationOptions;
import org.kevinkib.cards.domain.deck.DeckType;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cards.domain.hand.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * The deck a Bullshit game is played with. {@link #ranks()} lists the ranks present in the order the
 * rank claim mode cycles through them, so a short deck simply skips the absent ranks.
 */
public enum DeckSize {

    FULL("52", List.of(FrenchRank.ACE, FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE,
            FrenchRank.SIX, FrenchRank.SEVEN, FrenchRank.EIGHT, FrenchRank.NINE, FrenchRank.TEN,
            FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING)),
    SHORT("32", List.of(FrenchRank.SEVEN, FrenchRank.EIGHT, FrenchRank.NINE, FrenchRank.TEN,
            FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING, FrenchRank.ACE));

    /** The deck used when the host does not choose one. The only place this default lives. */
    public static final DeckSize DEFAULT = SHORT;

    private final String key;
    private final List<FrenchRank> ranks;

    DeckSize(String key, List<FrenchRank> ranks) {
        this.key = key;
        this.ranks = ranks;
    }

    public String key() {
        return key;
    }

    public List<FrenchRank> ranks() {
        return ranks;
    }

    public int cardCount() {
        return ranks.size() * FrenchSuit.getSuits().size();
    }

    /** Every card of this deck, unshuffled. */
    public List<Card> cards() {
        List<Card> cards = new ArrayList<>();
        for (FrenchRank rank : ranks) {
            for (FrenchSuit suit : FrenchSuit.getSuits()) {
                cards.add(new Card(rank, suit));
            }
        }
        return cards;
    }

    /** Shuffles this deck and deals all of it to {@code nbPlayers} hands. */
    public List<Hand> deal(int nbPlayers) {
        Deck deck = new CardsService().createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN));
        for (FrenchRank rank : FrenchRank.getRanks()) {
            if (!ranks.contains(rank)) {
                for (FrenchSuit suit : FrenchSuit.getSuits()) {
                    deck.remove(new Card(rank, suit));
                }
            }
        }
        return deck.distributeAll(nbPlayers);
    }

    /** Resolves a key to its size; unknown or {@code null} keys fall back to {@link #DEFAULT}. */
    public static DeckSize fromKey(String key) {
        for (DeckSize size : values()) {
            if (size.key.equals(key)) {
                return size;
            }
        }
        return DEFAULT;
    }
}
