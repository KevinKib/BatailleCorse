package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat is out of the game with this place (1 is the best). */
public record Eliminated(PlayerId seat, int place) implements NinetyNineEvent {
}
