/**
 * Session-management context — game-agnostic seating, tokens, and lifecycle around a hosted game.
 *
 * <p>Depends only on the shared kernel {@link org.kevinkib.cardgames.game}, never on a concrete
 * game: it stores and serves {@link org.kevinkib.cardgames.game.Game} instances keyed by
 * {@link org.kevinkib.cardgames.game.GameId}, seats players by
 * {@link org.kevinkib.cardgames.game.PlayerId}, and constructs games via
 * {@link org.kevinkib.cardgames.game.GameFactory}. This is what lets one session core host any game.
 */
package org.kevinkib.cardgames.sessionmanagement;
