package org.kevinkib.cardgames.threemenmorris.domain;

/** The point is free but the placement rule closes it for now (Picaria's centre). */
public class PointLockedException extends ThreeMenMorrisException {

    public PointLockedException(int point) {
        super("Point " + point + " cannot receive a placed piece");
    }
}
