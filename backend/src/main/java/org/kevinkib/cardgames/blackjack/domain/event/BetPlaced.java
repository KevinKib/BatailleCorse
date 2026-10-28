package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat placed its bet; the amount stays sealed until the deal. */
public record BetPlaced(PlayerId seat) implements BlackjackEvent {
}
