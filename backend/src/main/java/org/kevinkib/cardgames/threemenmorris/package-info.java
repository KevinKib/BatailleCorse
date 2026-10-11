/**
 * Three Men's Morris ("La petite marelle à trois pions") bounded context: a two-player abstract game
 * on a 3x3 board with the two diagonals through the centre.
 *
 * <p>{@code domain} holds the rules: the {@code ThreeMenMorris} aggregate (placement, movement, line
 * and blocked detection, forfeit), built on the shared kernel {@link org.kevinkib.cardgames.game}
 * only. It knows neither sessions, tokens nor transport. {@code presentation} holds the per-seat
 * state DTOs, the use cases shared by the WebSocket controller, the broadcasters and the REST
 * controller.
 */
package org.kevinkib.cardgames.threemenmorris;
