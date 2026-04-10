package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;

public abstract class Response {

    private final boolean success;
    private final EventType eventType;
    private final EventData eventData;
    private final String message;
    private final BatailleCorseDto state;

    public Response(boolean success, EventType eventType, EventData eventData, String message, BatailleCorseDto state) {
        this.success = success;
        this.eventType = eventType;
        this.eventData = eventData;
        this.message = message;
        this.state = state;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getEventType() {
        return eventType.toString();
    }

    public EventData getEventData() {
        return eventData;
    }

    public String getMessage() {
        return message;
    }

    public BatailleCorseDto getState() {
        return state;
    }
}
