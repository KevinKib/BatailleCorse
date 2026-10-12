package org.kevinkib.cardgames.sessionmanagement.core.domain;

/** Raised when the host asks to remove their own seat: the host leaves through Leave, not through a removal. */
public class CannotRemoveHostException extends RuntimeException {

    public CannotRemoveHostException() {
        super("The host cannot be removed from the lobby");
    }
}
