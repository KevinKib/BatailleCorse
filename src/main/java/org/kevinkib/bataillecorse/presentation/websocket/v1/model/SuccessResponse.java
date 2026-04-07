package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.presentation.websocket.v1.model.event.EventData;
import org.kevinkib.bataillecorse.presentation.websocket.v1.model.event.EventType;

public class SuccessResponse extends Response {

    public SuccessResponse(EventType eventType, EventData eventData, String message, BatailleCorseDto state) {
        super(true, eventType, eventData, message, state);
    }

}
