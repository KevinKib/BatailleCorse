package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/**
 * The seat's hand {@code handIndex} went over 21; its bet is lost at the settlement.
 *
 * @param value the hand total that busted
 */
public record Busted(PlayerId seat, int handIndex, int value) implements BlackjackEvent {
}
