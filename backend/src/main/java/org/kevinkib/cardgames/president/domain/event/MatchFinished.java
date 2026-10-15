package org.kevinkib.cardgames.president.domain.event;

/** The match is over. */
public record MatchFinished(PresidentResult result) implements PresidentEvent {
}
