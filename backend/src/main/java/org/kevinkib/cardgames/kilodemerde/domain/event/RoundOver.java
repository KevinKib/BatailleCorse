package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.kilodemerde.domain.RoundOutcome;

/** The round ended; the outcome reveals every hand. */
public record RoundOver(RoundOutcome outcome) implements KiloDeMerdeEvent {
}
