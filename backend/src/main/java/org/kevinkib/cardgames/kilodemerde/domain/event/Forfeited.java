package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat left the game; it is placed last. */
public record Forfeited(PlayerId seat) implements KiloDeMerdeEvent {
}
