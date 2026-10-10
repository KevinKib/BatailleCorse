package org.kevinkib.cardgames.bullshit.domain.claim;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Claims go by suit rather than by rank. A round starts on a randomly drawn suit (the same suit may
 * come up twice in a row) and, within the round, the suit follows the fixed cycle HEART, DIAMOND,
 * CLUB, SPADE, back to HEART. A round ends when a Bullshit call is resolved, which draws a new
 * starting suit. A claim is truthful when every played card is of the claimed suit.
 */
public class CyclingSuitClaimMode implements ClaimMode {

    private static final List<FrenchSuit> ORDER = FrenchSuit.getSuits();

    private final RandomGenerator random;

    public CyclingSuitClaimMode() {
        this(RandomGenerator.getDefault());
    }

    /** @param random the randomness port used to draw each round's starting suit */
    public CyclingSuitClaimMode(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public ClaimTarget initial() {
        return drawSuit();
    }

    @Override
    public ClaimTarget next(ClaimTarget current) {
        FrenchSuit suit = ((SuitTarget) current).suit();
        int nextIndex = (ORDER.indexOf(suit) + 1) % ORDER.size();
        return new SuitTarget(ORDER.get(nextIndex));
    }

    @Override
    public ClaimTarget nextRound(ClaimTarget current) {
        return drawSuit();
    }

    private SuitTarget drawSuit() {
        int index = Math.min((int) (random.nextDouble() * ORDER.size()), ORDER.size() - 1);
        return new SuitTarget(ORDER.get(index));
    }

    @Override
    public boolean matches(List<Card> cards, ClaimTarget target) {
        FrenchSuit expected = ((SuitTarget) target).suit();
        return cards.stream().allMatch(card -> card.getSuit() == expected);
    }
}
