package org.kevinkib.cardgames.bullshit.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.core.domain.GameMode;

public record BullshitCreatePayload(Integer nbPlayers, GameMode mode, String name) {
}
