package org.kevinkib.cardgames.threemenmorris.domain;

/** The player closed a mill and must take an opponent piece before anything else. */
public class RemovalPendingException extends ThreeMenMorrisException {

    public RemovalPendingException() {
        super("You closed a mill: remove an opponent piece first");
    }
}
