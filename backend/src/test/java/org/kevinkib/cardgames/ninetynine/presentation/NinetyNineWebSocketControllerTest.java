package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.presentation.api.NinetyNinePlayPayload;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;

class NinetyNineWebSocketControllerTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final NinetyNineTable table = new NinetyNineTable();

    private static CardDto dto(String code) {
        return CardDto.from(card(code));
    }

    @Test
    void startByTheHostBroadcastsTheFirstStateToEverySeat() {
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.sessions.joinRoom(new GameId(room.gameId()), "Cleo");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads).hasSize(3);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType()).isEqualTo("START");
        NinetyNineDto state = (NinetyNineDto) first.getState();
        assertThat(state.phase()).isEqualTo("PLAYING");
        assertThat(state.hand()).hasSize(3);
        assertThat(state.players()).hasSize(3);
    }

    @Test
    void startWithOnePlayerOrByANonHostRepliesAnErrorToTheSenderOnly() {
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");
        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
        assertThat(table.messaging.seats.get(0)).isEqualTo(S0);

        table.messaging.clear();
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats).containsExactly(S1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void startOnAnotherGameTypesRoomIsRejectedAndDoesNotStartThatGame() {
        SessionService both = new SessionService(table.repository,
                new GameFactories(List.of(new NinetyNineFactory(), new BullshitFactory())));
        NinetyNineWebSocketController ws = new NinetyNineWebSocketController(both, table.broadcaster,
                new SeatCommands(both, table.messaging), new NinetyNineGameActions(both, table.broadcaster));
        RoomCreated room = both.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        both.joinRoom(new GameId(room.gameId()), "Bob");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(both.findGame(new GameId(room.gameId()))).isEmpty();
        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
    }

    @Test
    void aPlayIsAuthenticatedByTheSeatToken() {
        NinetyNine game = table.start(2, "9S 7H 4S 8H 5S 2H 3C 5D");
        String id = game.getId().uuid().toString();

        table.ws.play(new NinetyNinePlayPayload(id, table.token(game, 0), dto("9S"), null));
        assertThat(game.pileTop()).contains(card("9S"));
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("PLAY");

        table.messaging.clear();
        table.ws.play(new NinetyNinePlayPayload(id, "not-a-token", dto("7H"), null));
        table.ws.play(new NinetyNinePlayPayload(id, null, dto("7H"), null));
        assertThat(game.pileTop()).contains(card("9S"));
        assertThat(table.messaging.payloads).isEmpty();
    }

    @Test
    void theAceValueIsPassedToTheGame() {
        NinetyNine game = table.start(2, "AS 7H 4S 8H 5S 2H 3C 5D");

        table.ws.play(new NinetyNinePlayPayload(game.getId().uuid().toString(), table.token(game, 0), dto("AS"), 11));

        assertThat(game.total()).isEqualTo(11);
    }

    @Test
    void aRefusedPlayRepliesToTheSenderOnlyWithItsCurrentState() throws Exception {
        NinetyNine game = table.atNinetyFour(GameOptions.of(Map.of("freePlay", "off")));
        String id = game.getId().uuid().toString();

        table.ws.play(new NinetyNinePlayPayload(id, table.token(game, 1), dto("7H"), null));

        assertThat(table.messaging.seats).containsExactly(S1);
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess()).isFalse();
        assertThat(error.getEventType()).isEqualTo("PLAY");
        assertThat(((ErrorResponse) error).getMessage()).isNotBlank();
        assertThat(((NinetyNineDto) error.getState()).total()).isEqualTo(94);
        assertThat(((NinetyNineDto) error.getState()).hand()).contains(dto("7H"));
    }

    @Test
    void aPlayOutOfTurnIsRefusedWithoutAnyEffect() {
        NinetyNine game = table.start(2, "9S 7H 4S 8H 5S 2H 3C 5D");

        table.ws.play(new NinetyNinePlayPayload(game.getId().uuid().toString(), table.token(game, 1), dto("7H"), null));

        assertThat(table.messaging.payloads).hasSize(1);
        assertThat(table.messaging.payloads.get(0).isSuccess()).isFalse();
        assertThat(game.version()).isZero();
    }

    @Test
    void aPlayWithoutACardOrWithAnUnknownCardIsRejectedWithoutAnyEffect() {
        NinetyNine game = table.start(2, "9S 7H 4S 8H 5S 2H 3C 5D");
        String id = game.getId().uuid().toString();
        String token = table.token(game, 0);

        table.ws.play(new NinetyNinePlayPayload(id, token, null, null));
        table.ws.play(new NinetyNinePlayPayload(id, token, new CardDto("NOPE", "HEART", "HEART_NOPE"), null));

        assertThat(table.messaging.payloads).hasSize(2);
        assertThat(table.messaging.payloads.stream().noneMatch(Response::isSuccess)).isTrue();
        assertThat(game.version()).isZero();
    }
}
