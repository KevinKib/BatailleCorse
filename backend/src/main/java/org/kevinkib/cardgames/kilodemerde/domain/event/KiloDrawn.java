package org.kevinkib.cardgames.kilodemerde.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/** A seat drew a kilo card (never a 7: that is {@link Flushed}); {@code card} is null when the pile was empty. */
public record KiloDrawn(PlayerId seat, Card card, int kilosAfter) implements KiloDeMerdeEvent {
}
