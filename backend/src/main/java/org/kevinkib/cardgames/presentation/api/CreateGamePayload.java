package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.core.domain.GameMode;

public record CreateGamePayload(GameMode mode, String name) {
}
