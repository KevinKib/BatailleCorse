package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseIdDto;

import java.util.Map;

public record CreateEventData(BatailleCorseIdDto game, Map<Integer, String> tokens) implements EventData {
}
