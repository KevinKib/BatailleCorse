package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;

public record CreateGamePayload(GameMode mode, String name) {
}
