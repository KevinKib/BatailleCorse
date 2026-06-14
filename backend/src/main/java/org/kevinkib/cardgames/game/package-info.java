/**
 * Shared kernel for the card-game platform.
 *
 * <p>Holds the game-agnostic abstractions that every game bounded context (e.g.
 * {@code bataillecorse}, {@code bullshit}) and the generic {@code sessionmanagement}
 * context depend on: {@link org.kevinkib.cardgames.game.Game},
 * {@link org.kevinkib.cardgames.game.GameId}, {@link org.kevinkib.cardgames.game.PlayerId}
 * and {@link org.kevinkib.cardgames.game.GameFactory}.
 *
 * <p>This is a DDD <em>shared kernel</em>: it is depended upon by multiple bounded
 * contexts, so anything here is a shared contract. Keep it minimal — only what the
 * session/lifecycle layer genuinely needs from any game belongs in it.
 */
package org.kevinkib.cardgames.game;
