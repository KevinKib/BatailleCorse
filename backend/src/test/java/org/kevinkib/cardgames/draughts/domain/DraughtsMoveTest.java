package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraughtsMoveTest {

    @Test
    void plainMoveNotationUsesADash() {
        DraughtsMove move = DraughtsMove.plain(32, 28);
        assertThat(move.notation()).isEqualTo("32-28");
        assertThat(move.isCapture()).isFalse();
        assertThat(move.path()).containsExactly(28);
        assertThat(move.captures()).isEmpty();
        assertThat(move.promotes()).isFalse();
    }

    @Test
    void captureNotationUsesAnX() {
        DraughtsMove move = new DraughtsMove(19, 30, List.of(30), List.of(24), false);
        assertThat(move.notation()).isEqualTo("19x30");
        assertThat(move.isCapture()).isTrue();
    }

    @Test
    void multiJumpNotationKeepsOnlyTheEnds() {
        DraughtsMove move = new DraughtsMove(33, 22, List.of(24, 22), List.of(29, 23), false);
        assertThat(move.notation()).isEqualTo("33x22");
    }
}
