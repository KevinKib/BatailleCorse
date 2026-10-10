package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

public class AscendingRankClaimMode implements ClaimMode {

    private final List<FrenchRank> order;

    public AscendingRankClaimMode() {
        this(DeckSize.FULL);
    }

    /** Cycles through the ranks of {@code deckSize} in ascending order, skipping ranks the deck lacks. */
    public AscendingRankClaimMode(DeckSize deckSize) {
        this.order = deckSize.ranks();
    }

    @Override
    public ClaimTarget initial() {
        return new RankTarget(order.get(0));
    }

    @Override
    public ClaimTarget next(ClaimTarget current) {
        FrenchRank rank = ((RankTarget) current).rank();
        int nextIndex = (order.indexOf(rank) + 1) % order.size();
        return new RankTarget(order.get(nextIndex));
    }

    @Override
    public boolean matches(List<Card> cards, ClaimTarget target) {
        FrenchRank expected = ((RankTarget) target).rank();
        return cards.stream().allMatch(card -> card.getRank() == expected);
    }
}
