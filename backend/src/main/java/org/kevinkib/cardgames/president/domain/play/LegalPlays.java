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

    /** A higher or equal set of the same count, or any set when leading. */
    public static List<Play> of(List<Card> hand, Play top, RankOrder order) {
        return of(hand, top, order, top == null ? Constraint.FREE : Constraint.BEAT);
    }

    /**
     * @param top        the play to beat, or {@code null} when leading (any set is legal)
     * @param constraint what the next play must answer; under {@link Constraint#MATCH_OR_PASS} only the set of the
     *                   same rank and count as the top is listed, and a lone 2 (it always closes the trick)
     */
    public static List<Play> of(List<Card> hand, Play top, RankOrder order, Constraint constraint) {
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
                if (top == null || BeatRule.answers(candidate, top, order, constraint)) {
                    plays.add(candidate);
                }
            }
        }
        return plays;
    }
}
