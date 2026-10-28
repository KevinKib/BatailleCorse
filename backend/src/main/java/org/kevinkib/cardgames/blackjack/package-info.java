/**
 * Blackjack bounded context: a seat card game against an automated dealer, played solo (one seat) or as a chip
 * tournament between 2 to 6 players. The game id is {@code blackjack}.
 *
 * <p>{@code domain} holds the rules, built on the shared kernel {@link org.kevinkib.cardgames.game} and FrenchCards
 * only. It knows neither sessions, tokens nor transport; {@code presentation} holds the adapters. This package
 * currently holds the shared types only (P0): nothing is registered and no behaviour is implemented yet.
 */
package org.kevinkib.cardgames.blackjack;
