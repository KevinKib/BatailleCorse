package org.kevinkib.cardgames.blackjack.domain.event;

/** The game is over. */
public record GameFinished(BlackjackResult result) implements BlackjackEvent {
}
