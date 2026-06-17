/**
 * Session-management — the multiplayer wrapper around a hosted game, split into two
 * collaborating sub-contexts:
 * <ul>
 *   <li>{@link org.kevinkib.cardgames.sessionmanagement.session session} — seating, tokens,
 *       and the create/join/start/rematch lifecycle of a hosted
 *       {@link org.kevinkib.cardgames.game.Game}.</li>
 *   <li>{@link org.kevinkib.cardgames.sessionmanagement.presence presence} — player liveness:
 *       connection tracking, the disconnect grace timer, and forfeiture.</li>
 * </ul>
 *
 * <p>Both depend only on the shared kernel {@link org.kevinkib.cardgames.game}, never on a concrete game.
 */
package org.kevinkib.cardgames.sessionmanagement;
