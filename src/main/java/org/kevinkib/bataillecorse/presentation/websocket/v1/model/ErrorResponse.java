package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.presentation.websocket.v1.model.event.EmptyEventData;
import org.kevinkib.bataillecorse.presentation.websocket.v1.model.event.EventType;

public class ErrorResponse extends Response {

    public ErrorResponse(EventType eventType, String message, BatailleCorseDto state) {
        super(false, eventType, new EmptyEventData(), message, state);
    }

}
