package org.kevinkib.cardgames.blackjack.presentation.api;

/** The bet of a seat for the hand, in whole points; a missing amount is refused. */
public record BlackjackBetPayload(String gameId, String token, Integer amount) {
}
