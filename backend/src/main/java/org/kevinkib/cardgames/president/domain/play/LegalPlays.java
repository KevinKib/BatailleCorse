package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every legal play of a hand against the top of a trick (sets only for now). One play per rank and count,
 * made of the first cards of that rank in the hand, from the weakest rank to the strongest.
 */
public final class LegalPlays {

    private LegalPlays() {
    }

    /** @param top the play to beat, or {@code null} when leading (any set is legal) */
    public static List<Play> of(List<Card> hand, Play top, RankOrder order) {
        Map<FrenchRank, List<Card>> byRank = new LinkedHashMap<>();
        for (Card card : hand) {
            byRank.computeIfAbsent((FrenchRank) card.getRank(), r -> new ArrayList<>()).add(card);
        }
        List<Play> plays = new ArrayList<>();
        for (FrenchRank rank : order.ranksAscending()) {
            List<Card> group = byRank.get(rank);
            if (group == null) {
                continue;
            }
            for (int count = 1; count <= group.size(); count++) {
                Play candidate = new Play(group.subList(0, count), rank);
                if (top == null || BeatRule.beats(candidate, top, order)) {
                    plays.add(candidate);
                }
            }
        }
        return plays;
    }
}
