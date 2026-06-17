package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;

public record CreateGamePayload(GameMode mode, String name) {
}
