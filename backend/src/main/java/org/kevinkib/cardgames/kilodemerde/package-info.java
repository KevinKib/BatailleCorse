/**
 * Kilo de merde bounded context: a real-time card game for 3 to 10 players (pass a card to the left, collect four of a
 * kind, slap the kilo pile; the last to slap takes "kilos"). The game id is {@code kilo-de-merde}.
 *
 * <p>{@code domain} holds the rules, built on the shared kernel {@link org.kevinkib.cardgames.game} and FrenchCards
 * only. It knows neither sessions, tokens nor transport. This package currently holds the shared types only (P0):
 * nothing is registered and the aggregate is not implemented yet.
 */
package org.kevinkib.cardgames.kilodemerde;
