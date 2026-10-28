package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A valid slap opened a race; {@code seat} is the first slapper. */
public record RaceOpened(PlayerId seat) implements KiloDeMerdeEvent {
}
