package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EmptyEventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;

public class ErrorResponse extends Response {

    public ErrorResponse(EventType eventType, String message, BatailleCorseDto state) {
        super(false, eventType, new EmptyEventData(), message, state);
    }

}
