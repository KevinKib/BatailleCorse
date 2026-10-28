package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat slapped in a race; {@code rank} is its place in the slap order (1 is the first slapper). */
public record Slapped(PlayerId seat, int rank) implements KiloDeMerdeEvent {
}
