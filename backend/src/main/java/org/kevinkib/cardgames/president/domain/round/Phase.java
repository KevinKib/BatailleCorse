package org.kevinkib.cardgames.president.domain.round;

/**
 * Where the game stands.
 *
 * <ul>
 *   <li>{@code EXCHANGE}: round 2 or later, the best cards of the low ranks moved, the high ranks give back;</li>
 *   <li>{@code EXCHANGE_VIEW}: round 2 or later with an exchange, every card has changed hands, the table is shown
 *       the exchange and every active seat acknowledges it; no trick yet;</li>
 *   <li>{@code PLAYING}: tricks;</li>
 *   <li>{@code ROUND_OVER}: the round is scored, the table waits for every player to be ready;</li>
 *   <li>{@code FINISHED}: the match is over (length reached, or a single player left).</li>
 * </ul>
 */
public enum Phase { EXCHANGE, EXCHANGE_VIEW, PLAYING, ROUND_OVER, FINISHED }
