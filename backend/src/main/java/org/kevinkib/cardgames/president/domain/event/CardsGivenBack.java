package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A high rank gave {@code count} cards back to a low rank. Only the count is public. */
public record CardsGivenBack(PlayerId from, PlayerId to, int count) implements PresidentEvent {
}
