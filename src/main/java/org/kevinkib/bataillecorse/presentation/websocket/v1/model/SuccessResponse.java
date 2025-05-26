package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

public class SuccessResponse extends Response {

    public SuccessResponse(EventType eventType, String message, BatailleCorseDto state) {
        super(true, eventType, message, state);
    }

}
