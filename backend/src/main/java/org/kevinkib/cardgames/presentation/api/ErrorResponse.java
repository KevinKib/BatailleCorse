package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;

/**
 * A failed command. {@code message} is an English diagnostic for logs and developers, never shown to players:
 * the client words every error from its code.
 */
public class ErrorResponse extends Response {

    public ErrorResponse(String eventType, String message, Object state) {
        super(false, eventType, new EmptyEventData(), message, state);
    }

}
