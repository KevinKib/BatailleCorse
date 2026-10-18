package org.kevinkib.cardgames.threemenmorris.domain;

/** The point to remove holds no piece of the opponent. */
public class NotOpponentPieceException extends ThreeMenMorrisException {

    public NotOpponentPieceException(int point) {
        super("Point " + point + " holds no opponent piece");
    }
}
