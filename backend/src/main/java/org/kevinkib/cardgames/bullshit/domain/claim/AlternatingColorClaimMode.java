package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.Color;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

/**
 * Claims alternate by colour rather than by rank: RED, then BLACK, then RED, … Each turn the
 * current player claims the forced colour and may bluff by playing off-colour cards. A claim is
 * truthful when every played card's suit is of the claimed colour.
 */
public class AlternatingColorClaimMode implements ClaimMode {

    @Override
    public ClaimTarget initial() {
        return new ColorTarget(Color.RED);
    }

    @Override
    public ClaimTarget next(ClaimTarget current) {
        Color colour = ((ColorTarget) current).color();
        return new ColorTarget(colour == Color.RED ? Color.BLACK : Color.RED);
    }

    @Override
    public boolean matches(List<Card> cards, ClaimTarget target) {
        Color expected = ((ColorTarget) target).color();
        return cards.stream().allMatch(card -> colourOf(card) == expected);
    }

    private static Color colourOf(Card card) {
        return ((FrenchSuit) card.getSuit()).getColor();
    }
}
