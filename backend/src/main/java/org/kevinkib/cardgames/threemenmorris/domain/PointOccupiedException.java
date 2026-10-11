package org.kevinkib.cardgames.threemenmorris.domain;

/** The target point already holds a piece. */
public class PointOccupiedException extends ThreeMenMorrisException {

    public PointOccupiedException(int point) {
        super("Point " + point + " is already occupied");
    }
}
