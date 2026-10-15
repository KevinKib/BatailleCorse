package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

public record Passed(PlayerId seat) implements PresidentEvent {
}
