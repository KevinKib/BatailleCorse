package org.kevinkib.cardgames.president.domain.rules;

/**
 * V16: who leads the first trick of round 1. The card only designates the leader, who opens with any legal
 * play and is not obliged to include it.
 */
public enum FirstLead implements ChoiceOption {
    /** The holder of the king of hearts (the owner's rule, the default). */
    KING_OF_HEARTS,
    /** The holder of the lowest club (the 3 of clubs). */
    LOWEST_CLUB,
    /** The holder of the queen of hearts. */
    QUEEN_OF_HEARTS,
    /** A seat drawn at random. */
    RANDOM
}
