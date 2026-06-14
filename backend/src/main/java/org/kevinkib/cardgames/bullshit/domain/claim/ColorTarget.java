package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.deck.french.Color;

public record ColorTarget(Color color) implements ClaimTarget {

    @Override
    public String label() {
        return color.toString();
    }
}
