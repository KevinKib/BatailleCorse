package org.kevinkib.cardgames.sessionmanagement.session.application;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Invalid token");
    }
}
