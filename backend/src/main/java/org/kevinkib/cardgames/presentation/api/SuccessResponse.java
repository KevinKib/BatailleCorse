package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.dto.event.EventType;

public class SuccessResponse extends Response {

    public SuccessResponse(EventType eventType, EventData eventData, String message, BatailleCorseDto state) {
        super(true, eventType, eventData, message, state);
    }

}
