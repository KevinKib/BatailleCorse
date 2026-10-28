package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A round was dealt; {@code starter} plays first. */
public record RoundDealt(int round, PlayerId starter) implements NinetyNineEvent {
}
