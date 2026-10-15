package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A player played their last card and takes finishing {@code place} (1 is the best). */
public record PlayerOut(PlayerId seat, int place) implements PresidentEvent {
}
