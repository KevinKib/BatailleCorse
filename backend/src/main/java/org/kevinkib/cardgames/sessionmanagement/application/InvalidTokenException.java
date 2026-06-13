package org.kevinkib.cardgames.sessionmanagement.application;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Invalid token");
    }
}
