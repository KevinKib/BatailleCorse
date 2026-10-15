package org.kevinkib.cardgames.president.domain.exchange;

import org.kevinkib.cardgames.game.PlayerId;

/**
 * One title pair of the exchange: {@code low} gave their best cards to {@code high}, who gives back
 * {@code count} cards. {@code done} once the return is made (or cancelled by a forfeit). It carries seats
 * and a count only, so it can be shown to every player.
 */
public record PendingReturn(PlayerId high, PlayerId low, int count, boolean done) {
}
