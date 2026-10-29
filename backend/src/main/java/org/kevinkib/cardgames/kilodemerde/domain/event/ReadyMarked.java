package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat confirmed it is ready for the next round (or the first one). */
public record ReadyMarked(PlayerId seat) implements KiloDeMerdeEvent {
}
