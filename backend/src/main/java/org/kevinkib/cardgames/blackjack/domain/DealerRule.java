package org.kevinkib.cardgames.blackjack.domain;

/** What the automated dealer does: it draws below 17 and stands on every 17 and above, soft or hard. */
public final class DealerRule {

    /** The total from which the dealer stands. */
    public static final int STAND_ON = 17;

    private DealerRule() {
    }

    /** Whether the dealer must draw another card with this hand. */
    public static boolean mustDraw(HandValue hand) {
        return hand.total() < STAND_ON;
    }
}
