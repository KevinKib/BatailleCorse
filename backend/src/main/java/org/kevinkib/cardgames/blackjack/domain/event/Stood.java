package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** The seat stands on its hand {@code handIndex}. */
public record Stood(PlayerId seat, int handIndex) implements BlackjackEvent {
}
