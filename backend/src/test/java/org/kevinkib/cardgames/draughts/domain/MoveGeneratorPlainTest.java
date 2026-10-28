package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveGeneratorPlainTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void theOpeningOffersNineMovesToWhiteAndNoneBackwards() {
        List<DraughtsMove> moves = generator.legalMoves(DraughtsPosition.initial());

        assertThat(Pos.notations(moves)).containsExactly(
                "31-26", "31-27", "32-27", "32-28", "33-28", "33-29", "34-29", "34-30", "35-30");
        assertThat(moves).allMatch(m -> !m.isCapture() && !m.promotes());
    }

    @Test
    void theOpeningOffersNineMovesToBlackToo() {
        DraughtsPosition blackToMove = DraughtsPosition.fromLayout(DraughtsPosition.initial().layout(), Colour.BLACK);

        assertThat(Pos.notations(generator.legalMoves(blackToMove))).containsExactly(
                "16-21", "17-21", "17-22", "18-22", "18-23", "19-23", "19-24", "20-24", "20-25");
    }

    @Test
    void aManNeverMovesBackwardsWithoutCapturing() {
        DraughtsPosition position = Pos.white("w28", "b1");

        assertThat(Pos.notations(generator.legalMoves(position))).containsExactly("28-22", "28-23");
    }

    @Test
    void aKingSlidesAlongEveryFreeDiagonal() {
        DraughtsPosition position = Pos.white("W46", "b1");

        // 46 is on the long diagonal 46-41-37-32-28-23-19-14-10-5
        assertThat(Pos.notations(generator.legalMoves(position))).containsExactly(
                "46-5", "46-10", "46-14", "46-19", "46-23", "46-28", "46-32", "46-37", "46-41");
    }

    @Test
    void aKingStopsBeforeAnyPieceInItsWay() {
        DraughtsPosition position = Pos.white("W46", "w32", "b1");

        List<String> moves = Pos.notations(generator.legalMoves(position));

        assertThat(moves).contains("46-37", "46-41").doesNotContain("46-32", "46-28", "46-23");
    }

    @Test
    void aBlockedSideHasNoLegalMove() {
        DraughtsPosition position = Pos.white("w31", "b26", "b27", "b22");

        assertThat(generator.legalMoves(position)).isEmpty();
    }

    @Test
    void aSideWithoutPiecesHasNoLegalMove() {
        assertThat(generator.legalMoves(Pos.white("b1"))).isEmpty();
    }
}
