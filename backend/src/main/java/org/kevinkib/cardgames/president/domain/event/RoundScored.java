package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.president.domain.scoring.RoundResult;

/** The round was scored. */
public record RoundScored(RoundResult result) implements PresidentEvent {
}
