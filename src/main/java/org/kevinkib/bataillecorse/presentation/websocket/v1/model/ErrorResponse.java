package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

public class ErrorResponse extends Response {

    public ErrorResponse(EventType eventType, String message, BatailleCorseDto state) {
        super(false, eventType, message, state);
    }

}
