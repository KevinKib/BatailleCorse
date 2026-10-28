package org.kevinkib.cardgames.draughts.domain;

import java.util.Arrays;

/**
 * An immutable position: the 50 squares and the colour to move. The storage is an implementation detail hidden
 * behind {@link #pieceAt(int)}.
 */
public record DraughtsPosition(Piece[] board, Colour toMove) {

    public DraughtsPosition {
        if (board == null || board.length != BoardGeometry.SQUARES + 1 || toMove == null) {
            throw new IllegalArgumentException("A position has 50 squares and a colour to move");
        }
        board = board.clone();
    }

    /** White men on 31-50, Black men on 1-20, 21-30 empty, White to move. */
    public static DraughtsPosition initial() {
        return fromLayout("b".repeat(20) + ".".repeat(10) + "w".repeat(20), Colour.WHITE);
    }

    /** Builds a position from 50 characters (square 1 first): {@code w W b B .}. */
    public static DraughtsPosition fromLayout(String layout, Colour toMove) {
        if (layout == null || layout.length() != BoardGeometry.SQUARES) {
            throw new IllegalArgumentException("A layout has exactly 50 characters");
        }
        Piece[] squares = new Piece[BoardGeometry.SQUARES + 1];
        for (int n = 1; n <= BoardGeometry.SQUARES; n++) {
            char c = layout.charAt(n - 1);
            squares[n] = c == '.' ? null : Piece.fromSymbol(c);
        }
        return new DraughtsPosition(squares, toMove);
    }

    /** The piece on {@code square} (1..50), or null when it is empty. */
    public Piece pieceAt(int square) {
        if (square < 1 || square > BoardGeometry.SQUARES) {
            throw new IllegalArgumentException("Square out of range: " + square);
        }
        return board[square];
    }

    @Override
    public Piece[] board() {
        return board.clone();
    }

    /** The 50 layout characters, square 1 first. */
    public String layout() {
        StringBuilder sb = new StringBuilder(BoardGeometry.SQUARES);
        for (int n = 1; n <= BoardGeometry.SQUARES; n++) {
            sb.append(board[n] == null ? '.' : board[n].symbol());
        }
        return sb.toString();
    }

    /** The repetition key: pieces (men and kings told apart) and the side to move. */
    public String key() {
        return layout() + (toMove == Colour.WHITE ? 'W' : 'B');
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DraughtsPosition p && toMove == p.toMove && Arrays.equals(board, p.board);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(board) + toMove.hashCode();
    }

    @Override
    public String toString() {
        return key();
    }
}
