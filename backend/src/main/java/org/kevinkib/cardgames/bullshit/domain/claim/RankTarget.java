package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.deck.french.FrenchRank;

public record RankTarget(FrenchRank rank) implements ClaimTarget {

    @Override
    public String label() {
        return rank.toString();
    }
}
