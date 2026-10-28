package org.kevinkib.cardgames.kilodemerde.domain.event;

/** Every chosen card moved to the next seat; {@code step} is the number of the step now starting. */
public record StepResolved(int round, int step) implements KiloDeMerdeEvent {
}
