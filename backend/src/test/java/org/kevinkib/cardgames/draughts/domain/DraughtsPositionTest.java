package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DraughtsPositionTest {

    private static String layout(char[] c) {
        return new String(c);
    }

    @Test
    void initialLayoutPlacesBlackOnOneToTwentyAndWhiteOnThirtyOneToFifty() {
        DraughtsPosition p = DraughtsPosition.initial();
        for (int n = 1; n <= 20; n++) {
            assertThat(p.pieceAt(n)).isEqualTo(Piece.BLACK_MAN);
        }
        for (int n = 21; n <= 30; n++) {
            assertThat(p.pieceAt(n)).isNull();
        }
        for (int n = 31; n <= 50; n++) {
            assertThat(p.pieceAt(n)).isEqualTo(Piece.WHITE_MAN);
        }
        assertThat(p.toMove()).isEqualTo(Colour.WHITE);
    }

    @Test
    void fromLayoutRoundTrips() {
        char[] c = new char[50];
        java.util.Arrays.fill(c, '.');
        c[0] = 'b';
        c[4] = 'B';
        c[27] = 'w';
        c[49] = 'W';
        DraughtsPosition p = DraughtsPosition.fromLayout(layout(c), Colour.BLACK);
        assertThat(p.pieceAt(1)).isEqualTo(Piece.BLACK_MAN);
        assertThat(p.pieceAt(5)).isEqualTo(Piece.BLACK_KING);
        assertThat(p.pieceAt(28)).isEqualTo(Piece.WHITE_MAN);
        assertThat(p.pieceAt(50)).isEqualTo(Piece.WHITE_KING);
        assertThat(p.toMove()).isEqualTo(Colour.BLACK);
        assertThat(p.layout()).isEqualTo(layout(c));
        assertThat(DraughtsPosition.initial().layout()).isEqualTo(
                "bbbbbbbbbbbbbbbbbbbb" + "..........".repeat(1) + "wwwwwwwwwwwwwwwwwwww");
    }

    @Test
    void fromLayoutRejectsBadInput() {
        assertThatThrownBy(() -> DraughtsPosition.fromLayout("bw.", Colour.WHITE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DraughtsPosition.fromLayout("x".repeat(50), Colour.WHITE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keySeparatesMenFromKingsAndTheSideToMove() {
        String base = ".".repeat(49);
        DraughtsPosition man = DraughtsPosition.fromLayout("w" + base, Colour.WHITE);
        DraughtsPosition king = DraughtsPosition.fromLayout("W" + base, Colour.WHITE);
        DraughtsPosition otherSide = DraughtsPosition.fromLayout("w" + base, Colour.BLACK);
        DraughtsPosition same = DraughtsPosition.fromLayout("w" + base, Colour.WHITE);
        assertThat(man.key()).isNotEqualTo(king.key());
        assertThat(man.key()).isNotEqualTo(otherSide.key());
        assertThat(man.key()).isEqualTo(same.key());
    }
}
