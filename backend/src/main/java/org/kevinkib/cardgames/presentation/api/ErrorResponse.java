package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;

public class ErrorResponse extends Response {

    public ErrorResponse(String eventType, String message, Object state) {
        super(false, eventType, new EmptyEventData(), message, state);
    }

}
