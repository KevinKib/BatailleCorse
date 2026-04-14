package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;

public class SuccessResponse extends Response {

    public SuccessResponse(EventType eventType, EventData eventData, String message, BatailleCorseDto state) {
        super(true, eventType, eventData, message, state);
    }

}
