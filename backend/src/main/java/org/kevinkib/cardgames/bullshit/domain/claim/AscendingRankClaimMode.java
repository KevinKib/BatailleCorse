package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

public class AscendingRankClaimMode implements ClaimMode {

    private static final List<FrenchRank> ORDER = List.of(
            FrenchRank.ACE, FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR,
            FrenchRank.FIVE, FrenchRank.SIX, FrenchRank.SEVEN, FrenchRank.EIGHT,
            FrenchRank.NINE, FrenchRank.TEN, FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING);

    @Override
    public ClaimTarget initial() {
        return new RankTarget(FrenchRank.ACE);
    }

    @Override
    public ClaimTarget next(ClaimTarget current) {
        FrenchRank rank = ((RankTarget) current).rank();
        int nextIndex = (ORDER.indexOf(rank) + 1) % ORDER.size();
        return new RankTarget(ORDER.get(nextIndex));
    }

    @Override
    public boolean matches(List<Card> cards, ClaimTarget target) {
        FrenchRank expected = ((RankTarget) target).rank();
        return cards.stream().allMatch(card -> card.getRank() == expected);
    }
}
