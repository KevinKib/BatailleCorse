package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/** A card was played; {@code aceValue} is set for an Ace only, {@code reversed} when the direction flipped. */
public record Played(PlayerId seat, Card card, Integer aceValue, int totalAfter, boolean reversed)
        implements NinetyNineEvent {
}
