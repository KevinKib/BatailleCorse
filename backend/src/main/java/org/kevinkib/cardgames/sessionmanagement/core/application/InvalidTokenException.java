package org.kevinkib.cardgames.sessionmanagement.core.application;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Invalid token");
    }
}
