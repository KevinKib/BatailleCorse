package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** The seat reached 0 points at the settlement of hand {@code handNumber} and is out of the game. */
public record Eliminated(PlayerId seat, int handNumber) implements BlackjackEvent {
}
