package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.deck.french.FrenchRank;

public record RankTarget(FrenchRank rank) implements ClaimTarget {

    @Override
    public String label() {
        return rank.toString();
    }
}
