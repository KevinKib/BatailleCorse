package org.kevinkib.cardgames.blackjack.presentation.api;

import org.kevinkib.cardgames.presentation.api.ErrorResponse;

/**
 * A refused Blackjack command: the generic error plus a stable {@code errorCode} the client words itself (the
 * message is an English diagnostic, never shown).
 */
public class BlackjackErrorResponse extends ErrorResponse {

    private final String errorCode;

    public BlackjackErrorResponse(String eventType, String message, String errorCode, Object state) {
        super(eventType, message, state);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
