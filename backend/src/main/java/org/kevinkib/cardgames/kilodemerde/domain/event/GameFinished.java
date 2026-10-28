package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeResult;

/** The game is over. */
public record GameFinished(KiloDeMerdeResult result) implements KiloDeMerdeEvent {
}
