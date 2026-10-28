package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

/** The seat forfeited: it is eliminated at once and its placed bet is lost. */
public record Forfeited(PlayerId seat) implements BlackjackEvent {
}
