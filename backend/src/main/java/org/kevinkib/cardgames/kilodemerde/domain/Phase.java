package org.kevinkib.cardgames.kilodemerde.domain;

/**
 * Where a Kilo de merde game stands: waiting for every seat to be ready before the first round, passing, a slap race,
 * between two rounds (the reveal, again waiting for every seat to be ready), or over.
 */
public enum Phase {
    AWAITING_START, PASSING, RACE, ROUND_OVER, FINISHED
}
