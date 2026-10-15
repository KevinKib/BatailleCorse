package org.kevinkib.cardgames.mangola.domain;

/** A layout must be 16 non-negative counts summing to 32. */
public class InvalidLayoutException extends MangolaException {

    public InvalidLayoutException(String reason) {
        super("Invalid layout: " + reason);
    }
}
