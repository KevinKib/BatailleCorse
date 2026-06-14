package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

/**
 * Claims cycle by suit rather than by rank: HEART, DIAMOND, CLUB, SPADE, then back to HEART. Each
 * turn the current player claims the forced suit and may bluff by playing off-suit cards. A claim
 * is truthful when every played card is of the claimed suit.
 */
public class CyclingSuitClaimMode implements ClaimMode {

    private static final List<FrenchSuit> ORDER = FrenchSuit.getSuits();

    @Override
    public ClaimTarget initial() {
        return new SuitTarget(ORDER.get(0));
    }

    @Override
    public ClaimTarget next(ClaimTarget current) {
        FrenchSuit suit = ((SuitTarget) current).suit();
        int nextIndex = (ORDER.indexOf(suit) + 1) % ORDER.size();
        return new SuitTarget(ORDER.get(nextIndex));
    }

    @Override
    public boolean matches(List<Card> cards, ClaimTarget target) {
        FrenchSuit expected = ((SuitTarget) target).suit();
        return cards.stream().allMatch(card -> card.getSuit() == expected);
    }
}
