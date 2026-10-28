package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat chose its card for the step; the card itself stays private. */
public record PassChosen(PlayerId seat) implements KiloDeMerdeEvent {
}
