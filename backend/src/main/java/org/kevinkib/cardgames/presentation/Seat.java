package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;

public record Seat(GameId gameId, PlayerId playerId) {
}
