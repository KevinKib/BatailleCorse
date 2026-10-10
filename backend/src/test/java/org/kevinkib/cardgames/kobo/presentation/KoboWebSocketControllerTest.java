package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.presentation.api.KoboCreatePayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboGiftPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboMatchPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboSlotPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboSwapPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboTargetPayload;
import org.kevinkib.cardgames.kobo.presentation.dto.CardDto;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboCreateEventData;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboWebSocketControllerTest {

    private final KoboTable table = new KoboTable();

    private KoboCreateEventData createRoom() {
        Response create = table.ws.createGame(new KoboCreatePayload("Alice"));
        return (KoboCreateEventData) create.getEventData();
    }

    private GameActionPayload action(Kobo game, int seat) {
        return new GameActionPayload(game.getId().uuid().toString(), table.token(game, seat));
    }

    // ---- lobby

    @Test
    void create_makesAKoboRoomAndReturnsTheHostToken() {
        Response create = table.ws.createGame(new KoboCreatePayload("Alice"));
        KoboCreateEventData data = (KoboCreateEventData) create.getEventData();

        assertThat(create.isSuccess(), is(true));
        assertThat(create.getEventType(), is("CREATE"));
        assertThat(data.gameType(), is("kobo"));
        assertThat(data.tokens().get(0), notNullValue());
        assertThat(table.sessions.gameType(new GameId(data.gameId())), is("kobo"));
    }

    @Test
    void start_byTheHostDealsTheGameAndBroadcastsOneStatePerSeat() {
        KoboCreateEventData room = createRoom();
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");

        table.ws.start(new GameActionPayload(room.gameId(), room.tokens().get(0)));

        assertThat(table.messaging.payloads, hasSize(2));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("START"));
        KoboDto state = (KoboDto) first.getState();
        assertThat(state.phase(), is("MEMORISING"));
        assertThat(state.startingCards(), hasSize(2));
        assertThat(((KoboDto) table.messaging.payloads.get(1).getState()).startingCards(), hasSize(2));
    }

    @Test
    void start_withTooFewPlayersOrByANonHost_repliesAnErrorToTheSenderOnly() {
        KoboCreateEventData room = createRoom();
        table.ws.start(new GameActionPayload(room.gameId(), room.tokens().get(0)));
        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.seats.get(0), is(seat(0)));

        table.messaging.clear();
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats, is(List.of(seat(1))));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
    }

    // ---- commands

    @Test
    void ready_thenDraw_throughTheWebSocketEndpoints() {
        KoboCreateEventData room = createRoom();
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.ws.start(new GameActionPayload(room.gameId(), room.tokens().get(0)));
        Kobo game = table.sessions.getGame(new GameId(room.gameId()), Kobo.class);
        table.messaging.clear();

        table.ws.ready(new GameActionPayload(room.gameId(), room.tokens().get(0)));
        table.ws.ready(new GameActionPayload(room.gameId(), table.token(game, 1)));
        table.messaging.clear();
        table.ws.draw(new GameActionPayload(room.gameId(), room.tokens().get(0)));

        assertThat(table.messaging.payloads.get(0).getEventType(), is("DRAW"));
        assertThat(game.phase(), is(Phase.DECISION));
    }

    @Test
    void anInvalidTokenIsIgnoredSilently() {
        Kobo game = table.install(aKobo().withPlayers(2).build());

        table.ws.draw(new GameActionPayload(game.getId().uuid().toString(), "not-a-token"));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void aDomainRejection_answersTheSenderAloneWithItsState() {
        Kobo game = table.install(aKobo().withPlayers(2).build());

        table.ws.draw(action(game, 1));

        assertThat(table.messaging.seats, is(List.of(seat(1))));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("DRAW"));
        assertThat(error.getState(), instanceOf(KoboDto.class));
    }

    @Test
    void everyCommandEndpointMapsItsPayloadOntoTheDomain() {
        Kobo game = table.install(aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDrawPile("QS", "7H", "9H", "KH", "2C", "3C").build());
        String gameId = game.getId().uuid().toString();
        String t0 = table.token(game, 0);

        table.ws.draw(new GameActionPayload(gameId, t0));                        // QS
        table.ws.discardDrawn(new GameActionPayload(gameId, t0));                // jack/queen power
        table.ws.blindSwap(new KoboSwapPayload(gameId, t0, 0, 1, 3));
        assertThat(game.slots(seat(0)).get(0).card(), is(card("KS")));
        table.ws.endTurn(new GameActionPayload(gameId, t0));
        String t1 = table.token(game, 1);
        table.ws.draw(new GameActionPayload(gameId, t1));                        // 7H
        table.ws.discardDrawn(new GameActionPayload(gameId, t1));
        table.ws.peek(new KoboTargetPayload(gameId, t1, 1, 0));
        assertThat(game.revealFor(seat(1)).get().card(), is(card("5S")));
        table.ws.endTurn(new GameActionPayload(gameId, t1));
        table.ws.draw(new GameActionPayload(gameId, t0));                        // 9H
        table.ws.swapDrawn(new KoboSlotPayload(gameId, t0, 1));
        assertThat(game.slots(seat(0)).get(1).card(), is(card("9H")));
        table.ws.endTurn(new GameActionPayload(gameId, t0));
        table.ws.draw(new GameActionPayload(gameId, t1));                        // KH
        table.ws.discardDrawn(new GameActionPayload(gameId, t1));
        table.ws.peek(new KoboTargetPayload(gameId, t1, 0, 2));
        table.ws.kingSwap(new KoboSlotPayload(gameId, t1, 2));
        assertThat(game.slots(seat(1)).get(2).card(), is(card("3H")));
        table.ws.skipPower(new GameActionPayload(gameId, t1));                   // wrong phase: error only
        table.ws.announce(new GameActionPayload(gameId, t1));
        assertThat(game.announcer().get(), is(seat(1)));
        assertThat(game.phase(), is(Phase.DRAW));
    }

    @Test
    void matchPassesTheGiveSlotTheExpectedRankAndTheRevision() {
        Kobo game = table.install(aKobo()
                .withTableau("7S", "2H", "3H", "4H").withTableau("5S", "6S", "7D", "KS")
                .withDiscard("7H").build());
        String gameId = game.getId().uuid().toString();
        String seven = CardDto.from(card("7H")).rank();

        table.ws.match(new KoboMatchPayload(gameId, table.token(game, 0), 1, 2, 1, seven, 0));

        assertThat(game.slots(seat(1)).get(2).card(), is(card("2H")));
        assertThat(game.slots(seat(0)).get(1).occupied(), is(false));
        table.messaging.clear();

        table.ws.match(new KoboMatchPayload(gameId, table.token(game, 0), 1, 2, 0, "KING", 0));

        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(game.handSize(seat(0)), is(3));
    }

    @Test
    void anUnknownRankLabelIsAnErrorForTheSenderWithoutPenalty() {
        Kobo game = table.install(aKobo().withTableau("7S", "2H", "3H", "4H").withTableau("5S", "6S", "7D", "KS")
                .withDiscard("7H").build());

        table.ws.match(new KoboMatchPayload(game.getId().uuid().toString(), table.token(game, 0), 0, 0, null,
                "NOPE", null));

        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(game.version(), is(0));
        assertThat(game.slots(seat(0)).size(), is(4));
    }

    @Test
    void giveTen_isAuthenticatedAndMappedToTheTarget() {
        Kobo game = table.install(aKobo().withTableau("AH", "KH", "KD", "AD").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").withDrawPile("2D", "3D").inPhase(Phase.TURN_END).build());
        String gameId = game.getId().uuid().toString();
        table.ws.announce(action(game, 0));
        table.ws.draw(action(game, 1));
        table.ws.discardDrawn(action(game, 1));
        table.ws.endTurn(action(game, 1));
        assertThat(game.phase(), is(Phase.GIFT));

        table.ws.giveTen(new KoboGiftPayload(gameId, table.token(game, 0), 1));

        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.total(new PlayerId(1)), is(43));
        assertThat(game.lastRoundResult().get().giftTo(), is(seat(1)));
        assertThat(((KoboDto) table.messaging.payloads.get(table.messaging.payloads.size() - 1).getState())
                .lastRound(), notNullValue());
        assertThat(table.messaging.payloads.get(table.messaging.payloads.size() - 1).getEventType(), is("ROUND_END"));
        assertThat(((KoboDto) table.messaging.payloads.get(0).getState()).lastRound(), nullValue());
    }
}
