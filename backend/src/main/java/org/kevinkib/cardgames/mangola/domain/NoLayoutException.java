package org.kevinkib.cardgames.mangola.domain;

/** The seat has no layout to confirm yet. */
public class NoLayoutException extends MangolaException {

    public NoLayoutException() {
        super("Arrange the seeds before confirming");
    }
}
