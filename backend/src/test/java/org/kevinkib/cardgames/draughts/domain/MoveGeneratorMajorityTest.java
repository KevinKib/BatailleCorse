package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveGeneratorMajorityTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void theLargestCaptureIsCompulsory() {
        // 27 may take 21 (one piece, landing on 16) or 22 then 13 (two pieces, landing on 9)
        DraughtsPosition position = Pos.white("w27", "b21", "b22", "b13");

        assertThat(Pos.notations(generator.legalMoves(position))).containsExactly("27x9");
    }

    @Test
    void threeMenMustBeTakenRatherThanTwoKings() {
        // option A: the man on 27 takes the kings on 21 and 11 (two pieces, landing on 16 then 7);
        // option B: the man on 48 takes the men on 43, 33 and 23 (three pieces, landing on 39, 28, 19).
        DraughtsPosition position = Pos.white("w27", "B21", "B11", "w48", "b43", "b33", "b23");

        List<DraughtsMove> moves = generator.legalMoves(position);

        DraughtsMove move = Pos.only(moves);
        assertThat(move.from()).isEqualTo(48);
        assertThat(move.captures()).hasSize(3);
    }

    @Test
    void aKingCountsForOnePieceAndGivesNoPriority() {
        // the man on 27 takes the black king on 21; the white king on 5 takes the black man on 14:
        // both capture one piece, both are listed
        DraughtsPosition position = Pos.white("w27", "B21", "W5", "b14");

        List<String> moves = Pos.notations(generator.legalMoves(position));

        assertThat(moves).contains("27x16", "5x19");
        assertThat(generator.legalMoves(position)).allMatch(m -> m.captures().size() == 1);
    }

    @Test
    void aManAndAKingWithTheSameCountBothCapture() {
        DraughtsPosition position = Pos.white("w27", "b21", "W5", "b14");

        assertThat(generator.legalMoves(position).stream().map(DraughtsMove::from).distinct().toList())
                .containsExactly(5, 27);
    }
}
