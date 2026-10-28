package org.kevinkib.cardgames.draughts.domain;

/** A side. White starts on squares 31-50 and moves first, towards row 0; Black starts on 1-20. */
public enum Colour {
    WHITE, BLACK;

    public Colour opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
