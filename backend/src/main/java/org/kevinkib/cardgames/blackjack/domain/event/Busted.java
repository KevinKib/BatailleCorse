package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** The seat's hand {@code handIndex} went over 21; its bet is lost at the settlement. */
public record Busted(PlayerId seat, int handIndex) implements BlackjackEvent {
}
