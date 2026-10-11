package org.kevinkib.cardgames.threemenmorris.domain;

/** A query names a point outside the board or a seat that does not play. Unchecked: it is a caller bug. */
public class InvalidQueryException extends IllegalArgumentException {

    public InvalidQueryException(String message) {
        super(message);
    }
}
