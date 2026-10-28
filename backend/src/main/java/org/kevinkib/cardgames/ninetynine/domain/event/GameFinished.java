package org.kevinkib.cardgames.ninetynine.domain.event;

/** The game is over. */
public record GameFinished(NinetyNineResult result) implements NinetyNineEvent {
}
