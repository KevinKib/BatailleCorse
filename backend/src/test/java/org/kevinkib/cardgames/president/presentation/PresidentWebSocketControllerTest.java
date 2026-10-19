package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.presentation.api.PresidentCardsPayload;
import org.kevinkib.cardgames.president.presentation.api.PresidentRoundPayload;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;

class PresidentWebSocketControllerTest {

    private final PresidentTable table = new PresidentTable();

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static List<CardDto> dtos(String... codes) {
        return java.util.Arrays.stream(codes).map(c -> CardDto.from(card(c))).toList();
    }

    private President threePlayers() {
        return table.install(aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C 8C").leader(0).build());
    }

    private GameActionPayload action(President game, int seat) {
        return new GameActionPayload(game.getId().uuid().toString(), table.token(game, seat));
    }

    private PresidentCardsPayload cards(President game, int seat, List<CardDto> cards) {
        return new PresidentCardsPayload(game.getId().uuid().toString(), table.token(game, seat), cards);
    }

    // ---- lobby

    @Test
    void start_byTheHostDealsTheGameAndBroadcastsOneStatePerSeat() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.sessions.joinRoom(new GameId(room.gameId()), "Carol");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads, hasSize(3));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("START"));
        PresidentDto state = (PresidentDto) first.getState();
        assertThat(state.phase(), is("PLAYING"));
        assertThat(state.round(), is(1));
        assertThat(state.hand().size() >= 17, is(true));
        assertThat(((PresidentDto) table.messaging.payloads.get(1).getState()).hand().size() >= 17, is(true));
    }

    @Test
    void start_onARoomOfAnotherGame_isRefusedWithoutStartingIt() {
        RoomCreated room = table.sessions.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        table.sessions.joinRoom(id, "Bob");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.payloads.get(0).getMessage(), is("This room is not a President game"));
        assertThat(table.messaging.seats.get(0), is(seat(0)));
        assertThat(table.sessions.findGame(id).isPresent(), is(false));
    }

    @Test
    void start_withTooFewPlayersOrByANonHost_repliesAnErrorToTheSenderOnly() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.seats.get(0), is(seat(0)));

        table.messaging.clear();
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Carol").token();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats, is(List.of(seat(2))));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
    }

    // ---- commands

    @Test
    void play_inTurnBroadcastsTheNewStateToEverySeat() {
        President game = threePlayers();

        table.ws.play(cards(game, 0, dtos("4C")));

        assertThat(table.messaging.payloads, hasSize(3));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("PLAY"));
        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.hand(seat(0)).size(), is(1));
    }

    @Test
    void pass_inTurnBroadcastsAPass() {
        President game = threePlayers();
        table.ws.play(cards(game, 0, dtos("4C")));
        table.messaging.clear();

        table.ws.pass(action(game, 1));

        assertThat(table.messaging.payloads.get(0).getEventType(), is("PASS"));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void aRefusedPlay_answersTheSenderAloneWithItsCurrentState() {
        President game = threePlayers();

        table.ws.play(cards(game, 1, dtos("5C")));

        assertThat(table.messaging.seats, is(List.of(seat(1))));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("PLAY"));
        assertThat(error.getState(), notNullValue());
        assertThat(((PresidentDto) error.getState()).hand().size(), is(2));
        assertThat(game.version(), is(0));
    }

    @Test
    void aPlayOfCardsTheSeatDoesNotHoldIsRefusedWithoutAnyEffect() {
        President game = threePlayers();

        table.ws.play(cards(game, 0, dtos("5C")));

        assertThat(table.messaging.seats, is(List.of(seat(0))));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(game.version(), is(0));
    }

    @Test
    void aPlayWithoutCardsOrWithAMalformedCardIsRefusedWithoutAnyEffect() {
        President game = threePlayers();
        String id = game.getId().uuid().toString();
        String token = table.token(game, 0);

        table.ws.play(new PresidentCardsPayload(id, token, null));
        table.ws.play(new PresidentCardsPayload(id, token, List.of()));
        table.ws.play(new PresidentCardsPayload(id, token, List.of(new CardDto("NOPE", "CLUB", "x"))));
        table.ws.giveBack(new PresidentCardsPayload(id, token, null));

        assertThat(table.messaging.payloads, hasSize(4));
        assertThat(table.messaging.payloads.stream().noneMatch(Response::isSuccess), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void aMalformedTokenIsIgnoredWithoutAnyReply() {
        President game = threePlayers();

        table.ws.play(new PresidentCardsPayload(game.getId().uuid().toString(), "stale", dtos("4C")));
        table.ws.pass(new GameActionPayload(game.getId().uuid().toString(), ""));
        table.ws.ready(new GameActionPayload("not-a-game", "x"));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }

    @Test
    void ready_andGiveBack_goThroughTheirOwnDestinations() throws Exception {
        President game = table.install(aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0)
                .build());
        table.ws.play(cards(game, 0, dtos("4C")));
        table.ws.play(cards(game, 1, dtos("5C")));
        table.ws.pass(action(game, 2));
        table.ws.play(cards(game, 1, dtos("6C")));
        assertThat(game.phase(), is(Phase.ROUND_OVER));

        for (int s = 0; s < 3; s++) {
            table.ws.ready(action(game, s));
        }
        assertThat(game.phase(), is(Phase.EXCHANGE));
        PlayerId president = game.exchange().get(0).high();
        List<CardDto> back = game.hand(president).subList(0, 2).stream().map(CardDto::from).toList();
        table.messaging.clear();

        table.ws.giveBack(cards(game, president.id(), back));

        assertThat(table.messaging.payloads.get(0).getEventType(), is("EXCHANGE"));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(((PresidentDto) table.messaging.payloads.get(0).getState()).phase(), is("PLAYING"));
    }

    @Test
    void seeExchange_outsideTheViewIsASilentNoOp() {
        President game = threePlayers();

        table.ws.seeExchange(new PresidentRoundPayload(game.getId().uuid().toString(), table.token(game, 0), 1));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void seeExchange_withoutARoundIsRefusedToTheSenderAlone() {
        President game = threePlayers();

        table.ws.seeExchange(new PresidentRoundPayload(game.getId().uuid().toString(), table.token(game, 0), null));

        assertThat(table.messaging.seats, is(List.of(seat(0))));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.payloads.get(0).getMessage(), is("Missing field: round"));
        assertThat(game.version(), is(0));
    }

    @Test
    void seeExchange_withAStaleTokenIsIgnoredWithoutAnyReply() {
        President game = threePlayers();

        table.ws.seeExchange(new PresidentRoundPayload(game.getId().uuid().toString(), "stale", 1));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }

    @Test
    void aGiveBackOutsideTheExchangeIsRefusedToTheSenderAlone() {
        President game = threePlayers();

        table.ws.giveBack(cards(game, 0, dtos("4C")));

        assertThat(table.messaging.seats, is(List.of(seat(0))));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("EXCHANGE"));
        assertThat(((PresidentDto) table.messaging.payloads.get(0).getState()).phase(), is("PLAYING"));
        assertThat(((PresidentDto) table.messaging.payloads.get(0).getState()).hand(), is(dtos("4C", "9C")));
    }
}
