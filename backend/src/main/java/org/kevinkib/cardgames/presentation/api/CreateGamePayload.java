package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.session.domain.GameMode;

public record CreateGamePayload(GameMode mode, String name) {
}
