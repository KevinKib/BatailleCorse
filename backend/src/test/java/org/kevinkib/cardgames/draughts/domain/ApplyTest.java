package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApplyTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void aPlainMoveMovesThePieceAndPassesTheTurn() {
        DraughtsPosition after = generator.apply(DraughtsPosition.initial(), DraughtsMove.plain(32, 28));

        assertThat(after.pieceAt(32)).isNull();
        assertThat(after.pieceAt(28)).isEqualTo(Piece.WHITE_MAN);
        assertThat(after.toMove()).isEqualTo(Colour.BLACK);
    }

    @Test
    void theInputPositionIsNotModified() {
        DraughtsPosition before = DraughtsPosition.initial();
        generator.apply(before, DraughtsMove.plain(32, 28));

        assertThat(before).isEqualTo(DraughtsPosition.initial());
    }

    @Test
    void aCaptureRemovesEveryCapturedPiece() {
        DraughtsPosition position = Pos.white("w27", "b22", "b13", "b1");
        DraughtsMove move = Pos.only(generator.legalMoves(position));

        DraughtsPosition after = generator.apply(position, move);

        assertThat(after.layout()).isEqualTo(Pos.white("w9", "b1").layout());
        assertThat(after.toMove()).isEqualTo(Colour.BLACK);
    }

    @Test
    void aRafleThatEndsOnItsStartSquareLeavesThePieceThere() {
        DraughtsPosition position = Pos.white("w28", "b33", "b34", "b24", "b23", "b1");
        DraughtsMove move = Pos.only(generator.legalMoves(position));

        DraughtsPosition after = generator.apply(position, move);

        assertThat(after.layout()).isEqualTo(Pos.white("w28", "b1").layout());
    }

    @Test
    void aPromotingMoveCrownsTheMan() {
        DraughtsPosition position = Pos.white("w8", "b50");
        DraughtsMove toThree = generator.legalMoves(position).stream()
                .filter(m -> m.to() == 3).findFirst().orElseThrow();

        DraughtsPosition after = generator.apply(position, toThree);

        assertThat(after.pieceAt(3)).isEqualTo(Piece.WHITE_KING);
    }

    @Test
    void aMoveThatDoesNotPromoteKeepsAMan() {
        DraughtsPosition position = Pos.white("w12", "b8", "b9");
        DraughtsPosition after = generator.apply(position, Pos.only(generator.legalMoves(position)));

        assertThat(after.pieceAt(14)).isEqualTo(Piece.WHITE_MAN);
    }

    @Test
    void aBlackMoveCrownsToABlackKing() {
        DraughtsPosition position = Pos.black("b41", "w1");
        DraughtsMove move = generator.legalMoves(position).get(0);

        DraughtsPosition after = generator.apply(position, move);

        assertThat(after.pieceAt(46)).isEqualTo(Piece.BLACK_KING);
        assertThat(after.toMove()).isEqualTo(Colour.WHITE);
    }

    @Test
    void movingAnEmptySquareOrTheOpponentsPieceIsRefused() {
        assertThatThrownBy(() -> generator.apply(DraughtsPosition.initial(), DraughtsMove.plain(28, 24)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.apply(DraughtsPosition.initial(), DraughtsMove.plain(16, 21)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
