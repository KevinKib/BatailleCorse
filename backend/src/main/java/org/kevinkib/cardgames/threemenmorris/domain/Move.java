package org.kevinkib.cardgames.threemenmorris.domain;

public record Move(int from, int to) {

    public Move {
        Board.requireInRange(from);
        Board.requireInRange(to);
    }
}
