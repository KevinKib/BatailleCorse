package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat slapped while nobody held four of a kind; it draws alone and the round ends. */
public record FalseSlap(PlayerId seat) implements KiloDeMerdeEvent {
}
