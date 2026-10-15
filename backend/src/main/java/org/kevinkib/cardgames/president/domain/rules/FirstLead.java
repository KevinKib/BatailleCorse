package org.kevinkib.cardgames.president.domain.rules;

/** V16: who leads the first trick of round 1. */
public enum FirstLead {
    /** The holder of the lowest club (the 3 of clubs). */
    LOWEST_CLUB,
    /** The holder of the queen of hearts. */
    QUEEN_OF_HEARTS,
    /** A seat drawn at random. */
    RANDOM
}
