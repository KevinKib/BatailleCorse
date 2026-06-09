package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

/** Identifies one player's seat in one game; the unit a presence/forfeit timer is keyed by. */
public record Seat(BatailleCorseId gameId, PlayerId playerId) {
}
