package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;

public record SlapEventData(boolean isSuccessful, PlayerIdDto player) implements EventData {
}
