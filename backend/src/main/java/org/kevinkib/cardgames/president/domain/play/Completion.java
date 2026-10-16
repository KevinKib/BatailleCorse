package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.List;

/**
 * The completion of the four (spec 3.1.1), pure: the cards of a rank that the chain at the top of the trick still
 * lacks, and whether a hand holds all of them. With one 52-card deck a rank has four cards, so at most one hand
 * holds the missing ones.
 */
public final class Completion {

    /** The cards of one rank in the deck. */
    public static final int RANK_SIZE = 4;

    private static final List<FrenchSuit> SUITS =
            List.of(FrenchSuit.HEART, FrenchSuit.DIAMOND, FrenchSuit.CLUB, FrenchSuit.SPADE);

    private Completion() {
    }

    /** The cards of {@code rank} that are not in the chain, one per missing suit. */
    public static List<Card> missing(FrenchRank rank, List<Card> chainCards) {
        List<Card> missing = new ArrayList<>();
        for (FrenchSuit suit : SUITS) {
            Card card = new Card(rank, suit);
            if (!chainCards.contains(card)) {
                missing.add(card);
            }
        }
        return missing;
    }

    /** The missing cards when the hand holds every one of them, else nothing. */
    public static List<Card> heldBy(List<Card> hand, FrenchRank rank, List<Card> chainCards) {
        List<Card> missing = missing(rank, chainCards);
        return !missing.isEmpty() && hand.containsAll(missing) ? missing : List.of();
    }

    /** Whether the cards are exactly the missing ones. */
    public static boolean isCompletion(List<Card> cards, FrenchRank rank, List<Card> chainCards) {
        List<Card> missing = missing(rank, chainCards);
        return !missing.isEmpty() && cards.size() == missing.size() && missing.containsAll(cards);
    }
}
