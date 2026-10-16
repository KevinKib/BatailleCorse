/**
 * The President computer player, pure domain: a view of what one seat may know, a per-seat memory fed by that
 * view, a heuristic strategy and its three levels. It depends on {@code president.domain}, the {@code game}
 * kernel and the card library only; it knows no session, transport or scheduling (the coordinator that runs
 * it at the table is a later step).
 */
package org.kevinkib.cardgames.president.domain.bot;
