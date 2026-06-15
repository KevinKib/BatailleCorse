package org.kevinkib.cardgames.bullshit.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;

public record BullshitCreatePayload(Integer nbPlayers, GameMode mode, String name) {
}
