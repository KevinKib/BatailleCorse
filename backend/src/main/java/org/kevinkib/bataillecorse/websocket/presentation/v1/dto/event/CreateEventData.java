package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import java.util.Map;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseIdDto;

public record CreateEventData(BatailleCorseIdDto game, Map<Integer, String> tokens) implements EventData {
}
