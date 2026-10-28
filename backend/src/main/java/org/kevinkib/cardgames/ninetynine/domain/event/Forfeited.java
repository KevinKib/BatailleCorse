package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat gave up and is eliminated at once. */
public record Forfeited(PlayerId seat) implements NinetyNineEvent {
}
