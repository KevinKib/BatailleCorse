package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A new round was dealt by {@code dealer} (the previous Scum from round 2). */
public record RoundDealt(int round, PlayerId dealer) implements PresidentEvent {
}
