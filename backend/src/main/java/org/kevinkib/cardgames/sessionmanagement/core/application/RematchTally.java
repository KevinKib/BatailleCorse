package org.kevinkib.cardgames.sessionmanagement.core.application;

/** Outcome of recording a rematch request among an eligible seat set. */
public record RematchTally(boolean unanimous, int ready, int eligible) {
}
