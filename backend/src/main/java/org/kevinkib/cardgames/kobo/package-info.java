/**
 * Kobo bounded context: a memory and speed card game for 2 to 6 players.
 *
 * <p>{@code domain} holds the rules: the {@code Kobo} aggregate (rounds, turns, powers, the
 * matching-discard race, scoring, forfeit), built on the shared kernel
 * {@link org.kevinkib.cardgames.game} and FrenchCards only. It knows neither sessions, tokens nor
 * transport. The aggregate sits at the root of {@code domain}; its concepts live in sub-packages
 * ({@code card}, {@code power}, {@code rules}, {@code scoring}, {@code tableau}, {@code turn},
 * {@code event}), each with the exceptions it protects. {@code presentation} holds the per-seat state DTOs, the use cases shared by the
 * WebSocket controller, the broadcasters and the REST controller.
 */
package org.kevinkib.cardgames.kobo;
