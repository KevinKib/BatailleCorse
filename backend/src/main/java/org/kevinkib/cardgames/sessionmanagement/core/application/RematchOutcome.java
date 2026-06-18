package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;

/**
 * Result of a rematch request: whether it started the fresh game, the game to surface
 * (fresh when started, the current one otherwise), and the request tally among eligible seats.
 */
public record RematchOutcome(boolean started, Game game, int ready, int eligible) {
}
