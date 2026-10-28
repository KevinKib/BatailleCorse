package org.kevinkib.cardgames.kilodemerde.domain.event;

/** A round was dealt. */
public record RoundDealt(int round) implements KiloDeMerdeEvent {
}
