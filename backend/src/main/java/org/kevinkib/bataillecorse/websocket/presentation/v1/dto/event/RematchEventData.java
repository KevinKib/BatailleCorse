package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;

public record RematchEventData(RematchStatus status, PlayerIdDto requestedBy) implements EventData {
}
