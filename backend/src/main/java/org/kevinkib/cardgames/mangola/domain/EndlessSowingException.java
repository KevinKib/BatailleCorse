package org.kevinkib.cardgames.mangola.domain;

/** The sowing of this pit never ends, and the rules make such a pit an illegal move. */
public class EndlessSowingException extends MangolaException {

    public EndlessSowingException(int pit) {
        super("Sowing pit " + MangolaGeometry.name(pit) + " would never end");
    }
}
