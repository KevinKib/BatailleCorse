package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.threemenmorris.domain.DrawOfferNotAllowedException;
import org.kevinkib.cardgames.threemenmorris.domain.NoDrawOfferException;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventData;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The draw use cases and endpoints: one broadcast per accepted command, the refusals answer the sender alone. */
class ThreeMenMorrisDrawActionsTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorrisTable table = new ThreeMenMorrisTable();

    /** Movement phase, white to move: white 0, 2, 7 and black 1, 5, 8. */
    private ThreeMenMorris movementGame() throws Exception {
        ThreeMenMorris game = table.newGame();
        for (int[] p : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 5}, {0, 7}, {1, 8}}) {
            table.actions.place(game.getId(), new PlayerId(p[0]), p[1]);
        }
        table.messaging.clear();
        return game;
    }

    private static ThreeMenMorrisEventData data(Response response) {
        return (ThreeMenMorrisEventData) response.getEventData();
    }

    @Test
    void anOfferIsBroadcastToBothSeatsAsDrawOffered() throws Exception {
        ThreeMenMorris game = movementGame();

        table.actions.offerDraw(game.getId(), BLACK);

        assertThat(table.messaging.seats, is(List.of(WHITE, BLACK)));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess(), is(true));
        assertThat(first.getEventType(), is("DRAW_OFFERED"));
        assertThat(data(first).seat(), is(1));
        assertThat(data(first).action(), is("OFFER_DRAW"));
        assertThat(((ThreeMenMorrisDto) first.getState()).drawOffer(), is(new ThreeMenMorrisDto.DrawOfferDto(1)));
    }

    @Test
    void aDeclineIsBroadcastAsDrawDeclinedAndTheOfferIsGone() throws Exception {
        ThreeMenMorris game = movementGame();
        table.actions.offerDraw(game.getId(), BLACK);
        table.messaging.clear();

        table.actions.declineDraw(game.getId(), WHITE);

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("DRAW_DECLINED"));
        assertThat(data(first).seat(), is(0));
        assertThat(data(first).action(), is("DECLINE_DRAW"));
        assertThat(((ThreeMenMorrisDto) first.getState()).drawOffer(), is(nullValue()));
    }

    @Test
    void anAcceptIsBroadcastAsGameOverWithTheAcceptDrawAction() throws Exception {
        ThreeMenMorris game = movementGame();
        table.actions.offerDraw(game.getId(), BLACK);
        table.messaging.clear();

        table.actions.acceptDraw(game.getId(), WHITE);

        assertThat(table.messaging.payloads, hasSize(2));
        for (Response response : table.messaging.payloads) {
            assertThat(response.getEventType(), is("GAME_OVER"));
            assertThat(data(response).action(), is("ACCEPT_DRAW"));
            assertThat(data(response).seat(), is(0));
            assertThat(((ThreeMenMorrisDto) response.getState()).outcome(),
                    is((ThreeMenMorrisDto.Outcome) new ThreeMenMorrisDto.Finished(null, "DRAW_AGREED")));
        }
    }

    @Test
    void aRepetitionDrawIsTheGameOverOfTheMoveThatCausedIt() throws Exception {
        ThreeMenMorris game = movementGame();
        for (int i = 0; i < 2; i++) {
            table.actions.move(game.getId(), WHITE, 7, 6);
            table.actions.move(game.getId(), BLACK, 8, 4);
            table.actions.move(game.getId(), WHITE, 6, 7);
            if (i == 0) {
                table.actions.move(game.getId(), BLACK, 4, 8);
            }
        }
        table.messaging.clear();

        table.actions.move(game.getId(), BLACK, 4, 8);

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("GAME_OVER"));
        assertThat(data(first).action(), is("MOVE"));
        assertThat(((ThreeMenMorrisDto) first.getState()).outcome(),
                is((ThreeMenMorrisDto.Outcome) new ThreeMenMorrisDto.Finished(null, "DRAW_REPETITION")));
    }

    @Test
    void aRejectedDrawCommandBroadcastsNothingAndPropagates() throws Exception {
        ThreeMenMorris game = movementGame();

        assertThrows(NoDrawOfferException.class, () -> table.actions.acceptDraw(game.getId(), BLACK));
        table.actions.offerDraw(game.getId(), WHITE);
        table.messaging.clear();
        assertThrows(DrawOfferNotAllowedException.class, () -> table.actions.acceptDraw(game.getId(), WHITE));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }

    @Test
    void theEndpointsAuthenticateTheSeatAndDriveTheDomain() throws Exception {
        ThreeMenMorris game = movementGame();
        String white = table.token(game, 0);
        String black = table.token(game, 1);
        String id = game.getId().uuid().toString();

        table.ws.offerDraw(new GameActionPayload(id, white));
        assertThat(game.drawOffer(), is(WHITE));
        table.ws.declineDraw(new GameActionPayload(id, black));
        assertThat(game.drawOffer(), is(nullValue()));
        table.ws.offerDraw(new GameActionPayload(id, black));
        table.ws.acceptDraw(new GameActionPayload(id, white));

        assertThat(game.result().isDraw(), is(true));
    }

    @Test
    void aRefusedEndpointCallAnswersTheSenderAloneWithItsState() throws Exception {
        ThreeMenMorris game = movementGame();
        String id = game.getId().uuid().toString();

        table.ws.acceptDraw(new GameActionPayload(id, table.token(game, 1)));
        table.ws.offerDraw(new GameActionPayload(id, "forged"));

        assertThat(table.messaging.seats, is(List.of(BLACK)));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("GAME_OVER"));
    }
}
