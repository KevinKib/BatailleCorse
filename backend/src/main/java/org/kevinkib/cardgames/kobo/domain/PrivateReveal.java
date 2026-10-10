package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cards.domain.Card;

/** A card a player has just looked at; visible to that player only. */
public record PrivateReveal(SlotRef ref, Card card, int revision) {
}
