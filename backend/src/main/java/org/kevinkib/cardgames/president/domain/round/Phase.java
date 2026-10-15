package org.kevinkib.cardgames.president.domain.round;

/**
 * Where the game stands. {@code PUTSCH} arrives with V4.
 *
 * <ul>
 *   <li>{@code EXCHANGE}: round 2 or later, the best cards of the low ranks moved, the high ranks give back;</li>
 *   <li>{@code PLAYING}: tricks;</li>
 *   <li>{@code ROUND_OVER}: the round is scored, the table waits for every player to be ready;</li>
 *   <li>{@code FINISHED}: the match is over (length reached, or a single player left).</li>
 * </ul>
 */
public enum Phase { EXCHANGE, PLAYING, ROUND_OVER, FINISHED }
