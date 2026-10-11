package org.kevinkib.cardgames.sessionmanagement.presence.domain;

import org.kevinkib.cardgames.game.GameId;

/**
 * What a connection claims: the seat token it presented for a game. The seat number is deliberately
 * not stored: it can change (seats shift when a player leaves a lobby) and is resolved from the
 * token when the connection drops.
 */
public record SeatClaim(GameId gameId, String token) {
}
