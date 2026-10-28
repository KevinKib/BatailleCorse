package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat drew the 7: its kilos and the 7 ({@code returnedCount} cards in all) went back into the pile. */
public record Flushed(PlayerId seat, int returnedCount) implements KiloDeMerdeEvent {
}
