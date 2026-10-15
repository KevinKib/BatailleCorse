package org.kevinkib.cardgames.mangola.domain;

/** A half of the board: side A owns rows 1 and 2 and starts, side B owns rows 3 and 4. */
public enum Side {
    A, B;

    public Side other() {
        return this == A ? B : A;
    }
}
