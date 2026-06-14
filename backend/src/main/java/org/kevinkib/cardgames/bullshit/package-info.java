/**
 * Bullshit bounded context — currently a self-contained {@code domain} rules engine.
 *
 * <p>It is slated to build on the shared kernel {@link org.kevinkib.cardgames.game} so it can be
 * hosted by the generic {@code sessionmanagement} context: its aggregate will implement
 * {@link org.kevinkib.cardgames.game.Game} (with a {@link org.kevinkib.cardgames.game.GameFactory}),
 * adopting the shared {@link org.kevinkib.cardgames.game.GameId} and
 * {@link org.kevinkib.cardgames.game.PlayerId} in place of its own id/player types. Until that
 * slice lands, this context does not yet depend on the kernel.
 */
package org.kevinkib.cardgames.bullshit;
