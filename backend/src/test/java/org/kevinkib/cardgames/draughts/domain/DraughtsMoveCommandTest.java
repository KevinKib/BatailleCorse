package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;

class DraughtsMoveCommandTest {

    @Test
    void whiteMovesFirstAndTheMoveIsRecorded() throws Exception {
        Draughts game = DraughtsGames.opening();

        DraughtsEvent event = game.move(WHITE, 32, 28, List.of());

        assertThat(event).isInstanceOf(DraughtsEvent.Moved.class);
        assertThat(((DraughtsEvent.Moved) event).seat()).isEqualTo(WHITE);
        assertThat(((DraughtsEvent.Moved) event).move().notation()).isEqualTo("32-28");
        assertThat(game.currentSeat()).isEqualTo(BLACK);
        assertThat(game.version()).isEqualTo(1);
        assertThat(game.history()).containsExactly("32-28");
        assertThat(game.lastMove().seat()).isEqualTo(WHITE);
        assertThat(game.position().pieceAt(28)).isEqualTo(Piece.WHITE_MAN);
        assertThat(game.position().pieceAt(32)).isNull();
    }

    @Test
    void theHostColourDecidesWhoStarts() throws Exception {
        Draughts game = new Draughts(GameId.generate(), BLACK, true);

        assertThat(game.currentSeat()).isEqualTo(BLACK);
        assertThat(game.colourOf(BLACK)).isEqualTo(Colour.WHITE);
        assertThat(game.colourOf(WHITE)).isEqualTo(Colour.BLACK);
        assertThatThrownBy(() -> game.move(WHITE, 17, 22, List.of())).isInstanceOf(NotPlayersTurnException.class);
        game.move(BLACK, 32, 28, List.of());
        assertThat(game.currentSeat()).isEqualTo(WHITE);
    }

    @Test
    void aMoveOutOfTurnIsRefusedAndChangesNothing() {
        Draughts game = DraughtsGames.opening();

        assertThatThrownBy(() -> game.move(BLACK, 17, 22, List.of())).isInstanceOf(NotPlayersTurnException.class);

        assertThat(game.version()).isZero();
        assertThat(game.history()).isEmpty();
    }

    @Test
    void anIllegalMoveIsRefusedAndChangesNothing() {
        Draughts game = DraughtsGames.opening();

        assertThatThrownBy(() -> game.move(WHITE, 32, 37, List.of())).isInstanceOf(IllegalMoveException.class);
        assertThatThrownBy(() -> game.move(WHITE, 20, 25, List.of())).isInstanceOf(IllegalMoveException.class);
        assertThatThrownBy(() -> game.move(WHITE, 32, 28, List.of(23))).isInstanceOf(IllegalMoveException.class);

        assertThat(game.version()).isZero();
    }

    @Test
    void aNullCaptureSquareIsAnInvalidSquareNotANullPointer() {
        Draughts game = DraughtsGames.opening();

        assertThatThrownBy(() -> game.move(WHITE, 32, 28, java.util.Arrays.asList((Integer) null)))
                .isInstanceOf(InvalidSquareException.class);
    }

    @Test
    void aSquareOutsideTheBoardIsRefused() {
        Draughts game = DraughtsGames.opening();

        assertThatThrownBy(() -> game.move(WHITE, 0, 28, List.of())).isInstanceOf(InvalidSquareException.class);
        assertThatThrownBy(() -> game.move(WHITE, 32, 51, List.of())).isInstanceOf(InvalidSquareException.class);
        assertThatThrownBy(() -> game.move(WHITE, 32, 28, List.of(99))).isInstanceOf(InvalidSquareException.class);
    }

    @Test
    void theCapturedSquaresAreMatchedAsASetInAnyOrder() throws Exception {
        // 32 takes 28 then 19 and lands on 14
        Draughts game = DraughtsGames.from(Pos.white("w32", "b28", "b19", "b1"));

        game.move(WHITE, 32, 14, List.of(19, 28));

        assertThat(game.position().pieceAt(14)).isEqualTo(Piece.WHITE_MAN);
        assertThat(game.position().pieceAt(28)).isNull();
        assertThat(game.position().pieceAt(19)).isNull();
        assertThat(game.history()).containsExactly("32x14");
    }

    @Test
    void aCaptureIsCompulsory() {
        Draughts game = DraughtsGames.from(Pos.white("w32", "b28", "b1", "w46"));

        assertThatThrownBy(() -> game.move(WHITE, 46, 41, List.of())).isInstanceOf(IllegalMoveException.class);
    }

    @Test
    void theSameDestinationWithDifferentCapturedSetsIsAmbiguousWithoutTheSet() throws Exception {
        // Black to move: the man on 11 reaches 15 over 7, 8, 19, 20 or over 17, 18, 19, 20
        DraughtsPosition position = DraughtsPosition.fromLayout(
                "..b.bwwwwbb.....wwww..b.....b...wb...bb.ww.wbbb.ww", Colour.BLACK);
        Draughts game = DraughtsGames.from(position);

        assertThat(game.currentSeat()).isEqualTo(BLACK);
        assertThatThrownBy(() -> game.move(BLACK, 11, 15, List.of())).isInstanceOf(AmbiguousMoveException.class);
        assertThat(game.version()).isZero();

        game.move(BLACK, 11, 15, List.of(17, 18, 19, 20));

        assertThat(game.position().pieceAt(15)).isEqualTo(Piece.BLACK_MAN);
    }

    @Test
    void anEmptyCapturedSetStandsForTheOnlyMatchingMove() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("w32", "b28", "b19", "b1"));

        game.move(WHITE, 32, 14, List.of());

        assertThat(game.history()).containsExactly("32x14");
    }

    @Test
    void aManReachingTheFarRowIsCrowned() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("w6", "b50"));

        game.move(WHITE, 6, 1, List.of());

        assertThat(game.position().pieceAt(1)).isEqualTo(Piece.WHITE_KING);
        assertThat(game.kings(WHITE)).isEqualTo(1);
        assertThat(game.men(WHITE)).isZero();
    }

    @Test
    void theLegalMovesAreForTheSeatOnTurnOnly() {
        Draughts game = DraughtsGames.opening();

        assertThat(game.legalMoves(WHITE)).hasSize(9);
        assertThat(game.legalMoves(BLACK)).isEmpty();
        assertThat(game.captureRequired(WHITE)).isFalse();
        assertThat(game.availableActions(WHITE)).contains(Action.MOVE);
        assertThat(game.availableActions(BLACK)).doesNotContain(Action.MOVE);
    }

    @Test
    void theCaptureHintCountsTheMajority() {
        Draughts game = DraughtsGames.from(Pos.white("w32", "b28", "b19", "b1"));

        assertThat(game.captureRequired(WHITE)).isTrue();
        assertThat(game.maxCaptures(WHITE)).isEqualTo(2);
        assertThat(game.captureRequired(BLACK)).isFalse();
        assertThat(game.maxCaptures(BLACK)).isZero();
    }

    @Test
    void theOpeningHasTwentyMenEach() {
        Draughts game = DraughtsGames.opening();

        assertThat(game.men(WHITE)).isEqualTo(20);
        assertThat(game.men(BLACK)).isEqualTo(20);
        assertThat(game.captured(WHITE)).isZero();
        assertThat(game.captured(BLACK)).isZero();
    }

    @Test
    void theLegalMovesAreGeneratedOncePerPositionWhateverTheNumberOfQueries() throws Exception {
        Draughts game = DraughtsGames.opening();
        int before = game.legalMoveGenerations();

        game.legalMoves(WHITE);
        game.captureRequired(WHITE);
        game.maxCaptures(WHITE);
        game.availableActions(BLACK);
        game.legalMoves(BLACK);

        assertThat(game.legalMoveGenerations()).isEqualTo(before + 1);

        game.move(WHITE, 32, 28, List.of());
        game.legalMoves(BLACK);
        game.captureRequired(BLACK);

        assertThat(game.legalMoveGenerations()).isEqualTo(before + 2);
    }
}
