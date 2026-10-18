package org.kevinkib.cardgames.threemenmorris.domain;

/** The piece is part of a mill and the opponent has a piece outside every mill: it cannot be taken. */
public class PieceInMillException extends ThreeMenMorrisException {

    public PieceInMillException(int point) {
        super("The piece on point " + point + " is protected by a mill");
    }
}
