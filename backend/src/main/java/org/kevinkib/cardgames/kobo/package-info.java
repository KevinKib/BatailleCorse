/**
 * Kobo bounded context: a memory and speed card game for 2 to 6 players.
 *
 * <p>{@code domain} holds the rules: the {@code Kobo} aggregate (rounds, turns, powers, the
 * matching-discard race, scoring, forfeit), built on the shared kernel
 * {@link org.kevinkib.cardgames.game} and FrenchCards only. It knows neither sessions, tokens nor
 * transport. {@code presentation} holds the per-seat state DTOs, the use cases shared by the
 * WebSocket controller, the broadcasters and the REST controller.
 */
package org.kevinkib.cardgames.kobo;
