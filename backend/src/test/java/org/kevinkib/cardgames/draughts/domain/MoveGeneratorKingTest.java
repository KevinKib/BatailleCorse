package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveGeneratorKingTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void aKingCapturesFromADistanceAndMayLandOnAnyFreeSquareBehind() {
        DraughtsPosition position = Pos.white("W46", "b32");

        List<DraughtsMove> moves = generator.legalMoves(position);

        assertThat(Pos.notations(moves)).containsExactly("46x5", "46x10", "46x14", "46x19", "46x23", "46x28");
        assertThat(moves).allMatch(m -> m.captures().equals(List.of(32)));
    }

    @Test
    void aKingTakesTwoSeparatedPiecesOnTheSameDiagonal() {
        DraughtsPosition position = Pos.white("W46", "b32", "b14");

        List<DraughtsMove> moves = generator.legalMoves(position);

        // 14 is a second capture on the same diagonal, taken from the first landing square (28, 23 or 19);
        // the largest capture (two pieces) is compulsory and the king lands on 10 or 5
        assertThat(Pos.notations(moves)).containsExactly("46x5", "46x10");
        assertThat(moves).allMatch(m -> m.captures().equals(List.of(32, 14)));
    }

    @Test
    void aKingCannotJumpTwoAdjacentPieces() {
        DraughtsPosition position = Pos.white("W46", "b37", "b32");

        assertThat(Pos.notations(generator.legalMoves(position))).containsExactly("46-41");
    }

    @Test
    void aKingCannotJumpItsOwnPieces() {
        DraughtsPosition position = Pos.white("W46", "w32", "b1");

        assertThat(generator.legalMoves(position)).noneMatch(DraughtsMove::isCapture);
    }

    @Test
    void aKingTurnsToContinueARafle() {
        // 46 jumps 32 (landing on 28), then turns south-east and jumps 39 (landing on 44 or 50)
        DraughtsPosition position = Pos.white("W46", "b32", "b39");

        List<DraughtsMove> moves = generator.legalMoves(position);

        assertThat(Pos.notations(moves)).containsExactly("46x44", "46x50");
        assertThat(moves.get(0).captures()).containsExactly(32, 39);
    }

    @Test
    void aCapturedPieceBlocksTheRestOfTheRafle() {
        // Turkish strike: after taking 33 and 34 the king stands on 30 or 25; if 34 left the board at once it could
        // go back over 39 and 43. The captured 34 is still an obstacle, so the best rafles take two pieces.
        DraughtsPosition position = Pos.white("W28", "b33", "b34", "b43");

        List<DraughtsMove> moves = generator.legalMoves(position);

        assertThat(Pos.notations(moves)).containsExactly("28x25", "28x30", "28x48");
        assertThat(moves).allMatch(m -> m.captures().size() == 2);
    }

    @Test
    void theStartSquareIsFreeDuringTheRafle() {
        // the king crosses and ends on its own start square
        DraughtsPosition position = Pos.white("W28", "b33", "b34", "b24", "b23");

        List<DraughtsMove> moves = generator.legalMoves(position);

        assertThat(Pos.notations(moves)).containsOnlyOnce("28x28");
        assertThat(moves).allMatch(m -> m.captures().size() == 4);
    }
}
