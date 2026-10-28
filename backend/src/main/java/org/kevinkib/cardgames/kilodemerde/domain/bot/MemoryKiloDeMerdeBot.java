package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeRules;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code hard} level (spec section 11): picks a target rank by {@code own count + 0.5 x cards of that rank
 * received this round}, never passes it, and passes first a rank it has already passed (it is travelling the ring
 * anyway), then the rank it holds fewest of. Deterministic: ties fall back to the rank order of
 * {@link KiloDeMerdeRules#defaultPass}.
 */
public class MemoryKiloDeMerdeBot implements KiloDeMerdeBotStrategy {

    /** The weight of a card of a rank received earlier this round when the target rank is chosen. */
    static final double RECEIVED_WEIGHT = 0.5;

    @Override
    public Card choosePass(KiloDeMerdeBotView view) {
        List<Card> hand = view.hand();
        if (hand.isEmpty()) {
            throw new IllegalStateException("Nothing to pass");
        }
        Map<FrenchRank, Integer> own = count(hand);
        Map<FrenchRank, Integer> received = new EnumMap<>(FrenchRank.class);
        Map<FrenchRank, Integer> given = new EnumMap<>(FrenchRank.class);
        for (KiloDeMerdeBotView.Pass pass : view.passHistory()) {
            received.merge(rank(pass.received()), 1, Integer::sum);
            given.merge(rank(pass.given()), 1, Integer::sum);
        }
        FrenchRank target = own.keySet().stream()
                .max(Comparator
                        .comparingDouble((FrenchRank r) -> own.get(r) + RECEIVED_WEIGHT * received.getOrDefault(r, 0))
                        .thenComparingInt(own::get)
                        .thenComparing(Comparator.comparingInt(FrenchRank::ordinal).reversed()))
                .orElseThrow();
        List<Card> candidates = hand.stream().filter(card -> rank(card) != target).toList();
        if (candidates.isEmpty()) {
            // a four of a kind: nothing to keep apart, the shared default is as good as any
            return KiloDeMerdeRules.defaultPass(hand);
        }
        return candidates.stream()
                .min(Comparator
                        .comparingInt((Card card) -> given.containsKey(rank(card)) ? 0 : 1)
                        .thenComparingInt(card -> own.get(rank(card)))
                        .thenComparingInt(card -> rank(card).ordinal())
                        .thenComparingInt(card -> ((FrenchSuit) card.getSuit()).ordinal()))
                .orElseThrow();
    }

    private static Map<FrenchRank, Integer> count(List<Card> cards) {
        Map<FrenchRank, Integer> counts = new EnumMap<>(FrenchRank.class);
        for (Card card : cards) {
            counts.merge(rank(card), 1, Integer::sum);
        }
        return counts;
    }

    private static FrenchRank rank(Card card) {
        return (FrenchRank) card.getRank();
    }
}
