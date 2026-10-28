package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/**
 * The last card played in the round: public state, so a reconnecting seat sees what the table saw.
 * {@code aceValue} is set for an Ace only.
 */
public record LastPlay(PlayerId seat, Card card, Integer aceValue, int totalBefore, int totalAfter,
                       boolean reversed) {
}
