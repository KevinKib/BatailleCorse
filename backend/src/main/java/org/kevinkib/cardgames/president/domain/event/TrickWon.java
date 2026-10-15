package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** The trick is over; {@code seat} leads the next one (the winner, or the next seat with cards). */
public record TrickWon(PlayerId seat, TrickEndReason reason) implements PresidentEvent {
}
