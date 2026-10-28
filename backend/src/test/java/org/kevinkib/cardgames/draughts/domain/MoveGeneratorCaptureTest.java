package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveGeneratorCaptureTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void captureIsCompulsoryAndHidesPlainMoves() {
        DraughtsPosition position = Pos.white("w28", "b23");

        assertThat(Pos.notations(generator.legalMoves(position))).containsExactly("28x19");
    }

    @Test
    void aManCapturesBackwards() {
        DraughtsPosition position = Pos.white("w28", "b33");

        DraughtsMove move = Pos.only(generator.legalMoves(position));

        assertThat(move.notation()).isEqualTo("28x39");
        assertThat(move.captures()).containsExactly(33);
    }

    @Test
    void aManCannotCaptureWhenTheSquareBehindIsTaken() {
        DraughtsPosition position = Pos.white("w28", "b23", "b19");

        assertThat(Pos.notations(generator.legalMoves(position))).containsExactly("28-22");
    }

    @Test
    void aManNeverCapturesItsOwnPieces() {
        DraughtsPosition position = Pos.white("w28", "w23");

        assertThat(generator.legalMoves(position)).noneMatch(DraughtsMove::isCapture);
    }

    @Test
    void aManMustContinueTheRafle() {
        // 27 jumps 22 to 18, then 13 to 9
        DraughtsPosition position = Pos.white("w27", "b22", "b13");

        DraughtsMove move = Pos.only(generator.legalMoves(position));

        assertThat(move.notation()).isEqualTo("27x9");
        assertThat(move.path()).containsExactly(18, 9);
        assertThat(move.captures()).containsExactly(22, 13);
    }

    @Test
    void aManCanTurnBackwardsInsideARafle() {
        // 28 jumps 23 to 19, then (backwards) 24 to 30
        DraughtsPosition position = Pos.white("w28", "b23", "b24");

        DraughtsMove move = Pos.only(generator.legalMoves(position));

        assertThat(move.notation()).isEqualTo("28x30");
        assertThat(move.captures()).containsExactly(23, 24);
    }

    @Test
    void aCapturedPieceStaysOnTheBoardAndCannotBeJumpedTwice() {
        // The ring 28-39-30-19-28 around 33, 34, 24, 23: the rafle ends on its own start square
        DraughtsPosition position = Pos.white("w28", "b33", "b34", "b24", "b23");

        DraughtsMove move = Pos.only(generator.legalMoves(position));

        assertThat(move.from()).isEqualTo(28);
        assertThat(move.to()).isEqualTo(28);
        assertThat(move.captures()).hasSize(4);
        assertThat(move.notation()).isEqualTo("28x28");
    }

    @Test
    void theRingCanBeTakenInEitherDirectionButIsOneMove() {
        // same start, same end, same four pieces: the two directions are one move (A8); a king may also stop
        // beyond its start square, which gives other moves, but "28x28" is listed once
        DraughtsPosition position = Pos.white("W28", "b33", "b34", "b24", "b23");

        List<DraughtsMove> moves = generator.legalMoves(position);

        assertThat(moves).allMatch(m -> m.captures().size() == 4);
        assertThat(Pos.notations(moves)).containsOnlyOnce("28x28");
    }

    @Test
    void sameStartAndEndWithDifferentCapturedSetsAreTwoMoves() {
        // Black man on 11: it reaches 15 either over 7 and 8 or over 17 and 18, then over 19 and 20 in both cases.
        // So the destination alone does not identify the move: the captured set does (found by random search).
        DraughtsPosition position = DraughtsPosition.fromLayout(
                "..b.bwwwwbb.....wwww..b.....b...wb...bb.ww.wbbb.ww", Colour.BLACK);

        List<DraughtsMove> toFifteen = generator.legalMoves(position).stream()
                .filter(m -> m.from() == 11 && m.to() == 15).toList();

        assertThat(toFifteen).hasSize(2);
        assertThat(toFifteen.get(0).captures()).containsExactlyInAnyOrder(7, 8, 19, 20);
        assertThat(toFifteen.get(1).captures()).containsExactlyInAnyOrder(17, 18, 19, 20);
    }
}

