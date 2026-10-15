/**
 * Mangola bounded context: the four-row sowing (mancala) game of the Congo, for two players, 32 seeds
 * each on a 4x8 board.
 *
 * <p>{@code domain} holds the rules: the board and its geometry, the rules as data
 * ({@code MangolaRules}), the relay sowing with the empty-pit rule, the capture and the detection of
 * endless sowings ({@code Sowing}) and the {@code Mangola} aggregate. It is built on the shared kernel
 * {@link org.kevinkib.cardgames.game} only and knows neither sessions, tokens nor transport.
 *
 * <p>{@code presentation} holds the adapters: the REST and STOMP controllers, the use cases shared by humans
 * (and, later, bots), the per-viewer DTO (the opponent's draft is never sent during the placement phase) and
 * the events. The beans are declared in {@code config.MangolaConfig}.
 */
package org.kevinkib.cardgames.mangola;
