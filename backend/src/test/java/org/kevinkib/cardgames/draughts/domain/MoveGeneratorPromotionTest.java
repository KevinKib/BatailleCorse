package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveGeneratorPromotionTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void aWhiteManReachingRowZeroPromotes() {
        List<DraughtsMove> moves = generator.legalMoves(Pos.white("w8", "b50"));

        assertThat(Pos.notations(moves)).containsExactly("8-2", "8-3");
        assertThat(moves).allMatch(DraughtsMove::promotes);
    }

    @Test
    void aBlackManReachingRowNinePromotes() {
        List<DraughtsMove> moves = generator.legalMoves(Pos.black("b41", "w1"));

        assertThat(Pos.notations(moves)).containsExactly("41-46", "41-47");
        assertThat(moves).allMatch(DraughtsMove::promotes);
    }

    @Test
    void aManThatEndsItsCaptureOnTheFarRowPromotes() {
        DraughtsMove move = Pos.only(generator.legalMoves(Pos.white("w12", "b8")));

        assertThat(move.notation()).isEqualTo("12x3");
        assertThat(move.promotes()).isTrue();
    }

    @Test
    void aManThatOnlyPassesOverTheFarRowStaysAMan() {
        // 12 jumps 8 and lands on 3 (row 0), then goes back over 9 to land on 14
        DraughtsMove move = Pos.only(generator.legalMoves(Pos.white("w12", "b8", "b9")));

        assertThat(move.notation()).isEqualTo("12x14");
        assertThat(move.path()).containsExactly(3, 14);
        assertThat(move.promotes()).isFalse();
    }

    @Test
    void aKingNeverPromotesAgain() {
        List<DraughtsMove> moves = generator.legalMoves(Pos.white("W8", "b50"));

        assertThat(moves).noneMatch(DraughtsMove::promotes);
    }
}
