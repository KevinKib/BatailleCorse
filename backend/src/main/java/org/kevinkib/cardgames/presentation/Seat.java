package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;

public record Seat(BatailleCorseId gameId, PlayerId playerId) {
}
