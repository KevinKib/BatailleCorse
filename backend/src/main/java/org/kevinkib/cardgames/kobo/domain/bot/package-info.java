/**
 * The Kobo computer player, pure domain: a view of what one seat may know, a per-seat memory fed by that view and
 * by public moves, a heuristic strategy and its three levels. It depends on {@code kobo.domain}, the {@code game}
 * kernel and the card library only; it knows no session, transport or scheduling.
 */
package org.kevinkib.cardgames.kobo.domain.bot;
