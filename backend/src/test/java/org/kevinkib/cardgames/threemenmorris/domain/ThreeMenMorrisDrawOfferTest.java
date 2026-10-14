package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Draw offers: movement phase only, one pending at a time, once between two own moves, cancelled by the next move. */
class ThreeMenMorrisDrawOfferTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    /** Movement phase, white to move: white 0, 2, 7 and black 1, 5, 8, no line, nobody blocked. */
    private ThreeMenMorris movement() {
        return ThreeMenMorris.fromLayout(GameId.generate(), "WBW..B.WB", 0, 0, WHITE);
    }

    @Test
    void anOfferIsRefusedDuringPlacement() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        assertThrows(DrawOfferNotAllowedException.class, () -> game.offerDraw(WHITE));

        assertThat(game.version(), is(0));
        assertThat(game.drawOffer(), is(nullValue()));
    }

    @Test
    void anOfferIsRefusedOnceTheGameIsFinished() {
        ThreeMenMorris game = movement();
        game.forfeit(BLACK);

        assertThrows(FinishedGameException.class, () -> game.offerDraw(WHITE));
    }

    @Test
    void aSeatMayOfferOnItsTurnOrOffTurnAndTheOfferIsPublic() throws Exception {
        ThreeMenMorris game = movement();
        int version = game.version();

        MorrisEvent event = game.offerDraw(BLACK);

        assertThat(event, is((MorrisEvent) new MorrisEvent.DrawOffered(BLACK)));
        assertThat(game.drawOffer(), is(BLACK));
        assertThat(game.version(), is(version + 1));
        assertThat(game.currentSeat(), is(WHITE));
    }

    @Test
    void onlyOneOfferIsPendingAtATime() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        int version = game.version();

        assertThrows(DrawOfferPendingException.class, () -> game.offerDraw(BLACK));
        assertThrows(DrawOfferPendingException.class, () -> game.offerDraw(WHITE));

        assertThat(game.version(), is(version));
        assertThat(game.drawOffer(), is(WHITE));
    }

    @Test
    void aSeatCannotOfferTwiceBetweenTwoOfItsOwnMoves() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        game.declineDraw(BLACK);

        assertThrows(DrawOfferNotAllowedException.class, () -> game.offerDraw(WHITE));

        game.move(WHITE, 7, 6);
        game.offerDraw(WHITE);
        assertThat(game.drawOffer(), is(WHITE));
    }

    @Test
    void theOpponentsMoveDoesNotGiveAnotherOfferBack() throws Exception {
        ThreeMenMorris game = movement();
        game.move(WHITE, 7, 6);
        game.offerDraw(WHITE);
        game.declineDraw(BLACK);
        game.move(BLACK, 8, 4);

        assertThrows(DrawOfferNotAllowedException.class, () -> game.offerDraw(WHITE));
        game.offerDraw(BLACK);
        assertThat(game.drawOffer(), is(BLACK));
    }

    @Test
    void acceptingFinishesWithNoWinner() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        int version = game.version();

        MorrisEvent event = game.acceptDraw(BLACK);

        assertThat(event, is((MorrisEvent) new MorrisEvent.DrawAccepted(BLACK)));
        assertThat(game.isFinished(), is(true));
        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new MorrisResult(null, MorrisResult.Reason.DRAW_AGREED)));
        assertThat(game.result().isDraw(), is(true));
        assertThat(game.drawOffer(), is(nullValue()));
        assertThat(game.version(), is(version + 1));
        assertThat(game.availableActions(WHITE), is(List.of()));
    }

    @Test
    void theOffererCannotAcceptOrDeclineItsOwnOffer() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        int version = game.version();

        assertThrows(DrawOfferNotAllowedException.class, () -> game.acceptDraw(WHITE));
        assertThrows(DrawOfferNotAllowedException.class, () -> game.declineDraw(WHITE));

        assertThat(game.version(), is(version));
        assertThat(game.drawOffer(), is(WHITE));
    }

    @Test
    void answeringWithoutAnOfferIsRefused() {
        ThreeMenMorris game = movement();

        assertThrows(NoDrawOfferException.class, () -> game.acceptDraw(BLACK));
        assertThrows(NoDrawOfferException.class, () -> game.declineDraw(BLACK));

        assertThat(game.version(), is(0));
    }

    @Test
    void decliningClearsTheOfferAndKeepsTheGameGoing() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);
        int version = game.version();

        MorrisEvent event = game.declineDraw(BLACK);

        assertThat(event, is((MorrisEvent) new MorrisEvent.DrawDeclined(BLACK)));
        assertThat(game.drawOffer(), is(nullValue()));
        assertThat(game.isFinished(), is(false));
        assertThat(game.version(), is(version + 1));
    }

    @Test
    void aMoveByEitherSideCancelsAPendingOffer() throws Exception {
        ThreeMenMorris byOfferer = movement();
        byOfferer.offerDraw(WHITE);
        byOfferer.move(WHITE, 7, 6);
        assertThat(byOfferer.drawOffer(), is(nullValue()));

        ThreeMenMorris byOfferee = movement();
        byOfferee.offerDraw(BLACK);
        byOfferee.move(WHITE, 7, 6);
        assertThat(byOfferee.drawOffer(), is(nullValue()));
        assertThrows(NoDrawOfferException.class, () -> byOfferee.acceptDraw(WHITE));
    }

    @Test
    void aRejectedMoveLeavesThePendingOfferAlone() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(BLACK);

        assertThrows(NotPlayersTurnException.class, () -> game.move(BLACK, 8, 4));
        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 2, 6));

        assertThat(game.drawOffer(), is(BLACK));
    }

    @Test
    void aForfeitWithAPendingOfferFinishesAsAForfeit() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(WHITE);

        game.forfeit(BLACK);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.FORFEIT)));
        assertThat(game.result().isDraw(), is(false));
        assertThat(game.drawOffer(), is(nullValue()));
    }

    @Test
    void drawActionsFollowTheOfferState() throws Exception {
        ThreeMenMorris game = movement();
        assertThat(game.drawActions(WHITE), is(List.of(Action.OFFER_DRAW)));
        assertThat(game.drawActions(BLACK), is(List.of(Action.OFFER_DRAW)));

        game.offerDraw(WHITE);
        assertThat(game.drawActions(WHITE), is(List.of()));
        assertThat(game.drawActions(BLACK), is(List.of(Action.ACCEPT_DRAW, Action.DECLINE_DRAW)));

        game.declineDraw(BLACK);
        assertThat(game.drawActions(WHITE), is(List.of()));
        assertThat(game.drawActions(BLACK), is(List.of(Action.OFFER_DRAW)));
    }

    @Test
    void noDrawActionsDuringPlacementOrOnceFinished() {
        ThreeMenMorris placing = new ThreeMenMorris(GameId.generate());
        assertThat(placing.drawActions(WHITE), is(List.of()));

        ThreeMenMorris over = movement();
        over.forfeit(BLACK);
        assertThat(over.drawActions(WHITE), is(List.of()));
        assertThat(over.drawActions(BLACK), is(List.of()));
    }

    @Test
    void theTurnActionsAreUnchangedByDraws() throws Exception {
        ThreeMenMorris game = movement();
        game.offerDraw(BLACK);

        assertThat(game.availableActions(WHITE), is(List.of(Action.MOVE)));
        assertThat(game.availableActions(BLACK), is(List.of()));
    }
}
