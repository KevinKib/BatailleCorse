package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;

public record CreateGamePayload(GameMode mode, String name) {
}
