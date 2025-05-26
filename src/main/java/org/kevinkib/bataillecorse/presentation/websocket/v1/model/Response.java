package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

public abstract class Response {

    private final boolean success;
    private final EventType eventType;
    private final String message;
    private final BatailleCorseDto state;

    public Response(boolean success, EventType eventType, String message, BatailleCorseDto state) {
        this.success = success;
        this.eventType = eventType;
        this.message = message;
        this.state = state;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getEventType() {
        return eventType.toString();
    }

    public String getMessage() {
        return message;
    }

    public BatailleCorseDto getState() {
        return state;
    }
}
