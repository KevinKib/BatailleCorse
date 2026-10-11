package org.kevinkib.cardgames.threemenmorris.domain;

/** The source point holds no piece of the player. */
public class NotYourPieceException extends ThreeMenMorrisException {

    public NotYourPieceException(int point) {
        super("You have no piece on point " + point);
    }
}
