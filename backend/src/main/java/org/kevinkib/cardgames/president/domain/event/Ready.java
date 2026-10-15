package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A player is ready for the next round; {@code opened} when they were the last one and the next round started. */
public record Ready(PlayerId seat, boolean opened) implements PresidentEvent {
}
