package org.kevinkib.cardgames.draughts.domain;

/** A piece on a square: a man or a king, of one colour. */
public enum Piece {
    WHITE_MAN('w', Colour.WHITE, false),
    WHITE_KING('W', Colour.WHITE, true),
    BLACK_MAN('b', Colour.BLACK, false),
    BLACK_KING('B', Colour.BLACK, true);

    private final char symbol;
    private final Colour colour;
    private final boolean king;

    Piece(char symbol, Colour colour, boolean king) {
        this.symbol = symbol;
        this.colour = colour;
        this.king = king;
    }

    /** The layout character: lower case for a man, upper case for a king; w white, b black. */
    public char symbol() {
        return symbol;
    }

    public Colour colour() {
        return colour;
    }

    public boolean isKing() {
        return king;
    }

    public static Piece fromSymbol(char symbol) {
        for (Piece p : values()) {
            if (p.symbol == symbol) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown piece symbol: " + symbol);
    }
}
