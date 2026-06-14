package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.EventType;

public class ErrorResponse extends Response {

    public ErrorResponse(EventType eventType, String message, BatailleCorseDto state) {
        super(false, eventType, new EmptyEventData(), message, state);
    }

}
