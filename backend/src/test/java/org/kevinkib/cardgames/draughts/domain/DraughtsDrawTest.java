package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraughtsDrawTest {

    // ---------------------------------------------------------- the automatic draws

    @Test
    void threefoldRepetitionWithTheSameSideToMoveIsADraw() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("W50", "W49", "B1", "B2"));

        for (int cycle = 0; cycle < 2; cycle++) {
            DraughtsGames.play(game, "50-44");
            DraughtsGames.play(game, "2-7");
            DraughtsGames.play(game, "44-50");
            assertThat(game.isFinished()).isFalse();
            DraughtsGames.play(game, "7-2");
            if (cycle == 0) {
                assertThat(game.repetitions()).isEqualTo(2);
                assertThat(game.isFinished()).isFalse();
            }
        }

        assertThat(game.result()).isEqualTo(new DraughtsResult(null, DraughtsResult.Reason.DRAW_REPETITION));
        assertThat(game.result().isDraw()).isTrue();
    }

    @Test
    void theSamePieceLayoutWithTheOtherSideToMoveDoesNotCountAsTheSamePosition() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("W50", "W49", "B1", "B2"));

        DraughtsGames.play(game, "50-44");
        DraughtsGames.play(game, "2-7");
        DraughtsGames.play(game, "44-50");
        DraughtsGames.play(game, "7-2");

        // back to the start with White to move: second occurrence, not yet a draw
        assertThat(game.repetitions()).isEqualTo(2);
        assertThat(game.isFinished()).isFalse();
    }

    @Test
    void fiftyPliesOfKingsOnlyAreADraw() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("W50", "W49", "B1", "B2"));

        DraughtsGames.playQuietPlies(game, 49);
        assertThat(game.isFinished()).isFalse();
        assertThat(game.draws().kingOnlyPlies()).isEqualTo(49);

        DraughtsGames.playQuietPlies(game, 1);

        assertThat(game.result()).isEqualTo(new DraughtsResult(null, DraughtsResult.Reason.DRAW_KINGS_ONLY));
    }

    @Test
    void aManMoveResetsTheKingsOnlyCount() throws Exception {
        // the quiet helper moves the kings on the low squares first; the white man on 46 is moved by hand
        Draughts game = DraughtsGames.from(Pos.white("W2", "W3", "w46", "B49", "B50"));
        DraughtsGames.playQuietPlies(game, 4);
        assertThat(game.draws().kingOnlyPlies()).isEqualTo(4);

        DraughtsGames.play(game, "46-41");

        assertThat(game.draws().kingOnlyPlies()).isZero();
    }

    @Test
    void threeKingsAgainstALoneKingDrawAfterSixteenMovesEach() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("W50", "W49", "W48", "B1"));
        assertThat(game.draws().endgameLimitPlies()).isEqualTo(32);

        DraughtsGames.playQuietPlies(game, 31);
        assertThat(game.isFinished()).isFalse();
        DraughtsGames.playQuietPlies(game, 1);

        assertThat(game.result()).isEqualTo(new DraughtsResult(null, DraughtsResult.Reason.DRAW_ENDGAME));
    }

    @Test
    void twoKingsAgainstALoneKingDrawAfterFiveMovesEach() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("W50", "W49", "B1"));
        assertThat(game.draws().endgameLimitPlies()).isEqualTo(10);

        DraughtsGames.playQuietPlies(game, 9);
        assertThat(game.isFinished()).isFalse();
        DraughtsGames.playQuietPlies(game, 1);

        assertThat(game.result()).isEqualTo(new DraughtsResult(null, DraughtsResult.Reason.DRAW_ENDGAME));
    }

    @Test
    void aCaptureThatChangesTheMaterialStartsTheCountOfTheNewCase() throws Exception {
        // three white kings against the lone black king on 5, which can take the king on 14 (landing 19..41)
        Draughts game = DraughtsGames.from(Pos.white("B5", "W14", "W46", "W50"));
        assertThat(game.draws().endgameLimitPlies()).isEqualTo(32);
        DraughtsGames.play(game, "50-44");
        assertThat(game.draws().endgamePlies()).isEqualTo(1);

        DraughtsMove capture = game.legalMoves(BLACK).get(0);
        assertThat(capture.isCapture()).isTrue();
        game.move(BLACK, capture.from(), capture.to(), capture.captures());

        assertThat(game.draws().endgameLimitPlies()).isEqualTo(10);
        assertThat(game.draws().endgamePlies()).isZero();
        assertThat(game.isFinished()).isFalse();
    }

    // ---------------------------------------------------------------- draw offers

    @Test
    void anAcceptedOfferEndsTheGameAsADraw() throws Exception {
        Draughts game = DraughtsGames.opening();

        DraughtsEvent offered = game.offerDraw(BLACK);
        assertThat(offered).isEqualTo(new DraughtsEvent.DrawOffered(BLACK));
        assertThat(game.drawOffer()).isEqualTo(BLACK);
        assertThat(game.availableActions(WHITE)).contains(Action.ACCEPT_DRAW, Action.DECLINE_DRAW);
        assertThat(game.availableActions(BLACK)).doesNotContain(Action.OFFER_DRAW, Action.ACCEPT_DRAW);

        DraughtsEvent accepted = game.acceptDraw(WHITE);

        assertThat(accepted).isEqualTo(new DraughtsEvent.DrawAccepted(WHITE));
        assertThat(game.result()).isEqualTo(new DraughtsResult(null, DraughtsResult.Reason.DRAW_AGREED));
        assertThat(game.isFinished()).isTrue();
        assertThat(game.drawOffer()).isNull();
    }

    @Test
    void aDeclinedOfferLeavesTheGameGoingAndTheOffererCannotInsistBeforeMoving() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(WHITE);

        DraughtsEvent declined = game.declineDraw(BLACK);

        assertThat(declined).isEqualTo(new DraughtsEvent.DrawDeclined(BLACK));
        assertThat(game.drawOffer()).isNull();
        assertThat(game.isFinished()).isFalse();
        assertThat(game.availableActions(WHITE)).doesNotContain(Action.OFFER_DRAW);
        assertThatThrownBy(() -> game.offerDraw(WHITE)).isInstanceOf(DrawOfferNotAllowedException.class);

        game.move(WHITE, 32, 28, List.of());
        game.move(BLACK, 17, 22, List.of());

        assertThat(game.availableActions(WHITE)).contains(Action.OFFER_DRAW);
        game.offerDraw(WHITE);
    }

    @Test
    void theProposersOwnMoveDoesNotWithdrawItsOffer() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(WHITE);

        game.move(WHITE, 32, 28, List.of());

        assertThat(game.drawOffer()).isEqualTo(WHITE);
        assertThat(game.availableActions(BLACK)).contains(Action.ACCEPT_DRAW, Action.DECLINE_DRAW);
        assertThat(game.availableActions(WHITE)).doesNotContain(Action.OFFER_DRAW);
        game.acceptDraw(BLACK);
        assertThat(game.result()).isEqualTo(new DraughtsResult(null, DraughtsResult.Reason.DRAW_AGREED));
    }

    @Test
    void theOpponentMovingDeclinesThePendingOffer() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(BLACK);

        game.move(WHITE, 32, 28, List.of());

        assertThat(game.drawOffer()).isNull();
        assertThat(game.availableActions(BLACK)).doesNotContain(Action.ACCEPT_DRAW);
        assertThatThrownBy(() -> game.acceptDraw(WHITE)).isInstanceOf(NoDrawOfferException.class);
    }

    @Test
    void anOfferIsRefusedWhenOffersAreOff() {
        Draughts game = DraughtsGames.fromWithoutOffers(DraughtsPosition.initial());

        assertThat(game.drawOffersAllowed()).isFalse();
        assertThat(game.availableActions(WHITE)).doesNotContain(Action.OFFER_DRAW);
        assertThatThrownBy(() -> game.offerDraw(WHITE)).isInstanceOf(DrawOfferNotAllowedException.class);
        assertThat(game.version()).isZero();
    }

    @Test
    void onlyOneOfferMayBePendingAndTheOffererCannotAnswerIt() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(WHITE);

        assertThatThrownBy(() -> game.offerDraw(BLACK)).isInstanceOf(DrawOfferPendingException.class);
        assertThatThrownBy(() -> game.acceptDraw(WHITE)).isInstanceOf(DrawOfferNotAllowedException.class);
        assertThatThrownBy(() -> game.declineDraw(WHITE)).isInstanceOf(DrawOfferNotAllowedException.class);
        assertThat(game.drawOffer()).isEqualTo(WHITE);
    }

    @Test
    void answeringWithoutAnOfferIsRefused() {
        Draughts game = DraughtsGames.opening();

        assertThatThrownBy(() -> game.acceptDraw(WHITE)).isInstanceOf(NoDrawOfferException.class);
        assertThatThrownBy(() -> game.declineDraw(BLACK)).isInstanceOf(NoDrawOfferException.class);
    }

    @Test
    void drawCommandsOnAFinishedGameAreRefused() {
        Draughts game = DraughtsGames.opening();
        game.forfeit(WHITE);

        assertThatThrownBy(() -> game.offerDraw(BLACK)).isInstanceOf(FinishedGameException.class);
        assertThatThrownBy(() -> game.acceptDraw(BLACK)).isInstanceOf(FinishedGameException.class);
    }

    @Test
    void everyAcceptedDrawCommandBumpsTheVersionAndARefusedOneDoesNot() throws Exception {
        Draughts game = DraughtsGames.opening();

        game.offerDraw(WHITE);
        assertThat(game.version()).isEqualTo(1);
        assertThatThrownBy(() -> game.offerDraw(BLACK)).isInstanceOf(DrawOfferPendingException.class);
        assertThat(game.version()).isEqualTo(1);
        game.declineDraw(BLACK);
        assertThat(game.version()).isEqualTo(2);
    }
}
