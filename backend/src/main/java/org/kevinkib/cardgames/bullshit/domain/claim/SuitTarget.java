package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.deck.french.FrenchSuit;

public record SuitTarget(FrenchSuit suit) implements ClaimTarget {

    @Override
    public String label() {
        return suit.toString();
    }
}
