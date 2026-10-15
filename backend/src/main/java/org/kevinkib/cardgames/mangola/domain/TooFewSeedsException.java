package org.kevinkib.cardgames.mangola.domain;

/** The pit holds fewer seeds than the rules require to be sown. */
public class TooFewSeedsException extends MangolaException {

    public TooFewSeedsException(int pit, int minimum) {
        super("Pit " + MangolaGeometry.name(pit) + " needs at least " + minimum + " seeds");
    }
}
