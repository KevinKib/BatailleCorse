package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/**
 * One draw from the kilo pile: {@code card} is null when the pile was empty (the draw is skipped, A9),
 * {@code flushed} when the card was a 7 and the drawer's kilos went back into the pile (A8).
 */
public record KiloDraw(PlayerId seat, Card card, boolean flushed, int kilosAfter) {
}
