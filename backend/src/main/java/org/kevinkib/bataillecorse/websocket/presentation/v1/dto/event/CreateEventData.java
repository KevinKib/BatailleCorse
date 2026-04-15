package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseIdDto;

public record CreateEventData(BatailleCorseIdDto game) implements EventData {
}
