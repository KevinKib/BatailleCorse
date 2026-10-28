/**
 * Ninety-Nine (99) bounded context: a counting card game with lives for 2 to 12 players. The game id is
 * {@code ninety-nine} (a digit cannot start a Java package), the display name is "99".
 *
 * <p>{@code domain} holds the rules, built on the shared kernel {@link org.kevinkib.cardgames.game} and FrenchCards
 * only. It knows neither sessions, tokens nor transport. This package currently holds the shared types only (P0):
 * nothing is registered and no behaviour is implemented yet.
 */
package org.kevinkib.cardgames.ninetynine;
