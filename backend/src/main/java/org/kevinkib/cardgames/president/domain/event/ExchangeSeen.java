package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** A seat has seen this round's exchange; {@code playBegins} when they were the last one and the first trick starts. */
public record ExchangeSeen(PlayerId seat, boolean playBegins) implements PresidentEvent {
}
