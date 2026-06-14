package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

public abstract class Response {

    private final boolean success;
    private final String eventType;
    private final EventData eventData;
    private final String message;
    private final Object state;

    public Response(boolean success, String eventType, EventData eventData, String message, Object state) {
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
        return eventType;
    }

    public EventData getEventData() {
        return eventData;
    }

    public String getMessage() {
        return message;
    }

    public Object getState() {
        return state;
    }
}
