package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

public class SuccessResponse extends Response {

    public SuccessResponse(String eventType, EventData eventData, String message, Object state) {
        super(true, eventType, eventData, message, state);
    }

}
