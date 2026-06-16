package org.kevinkib.cardgames.sessionmanagement.presence.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

public record Seat(GameId gameId, PlayerId playerId) {
}
