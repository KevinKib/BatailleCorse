package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.api.ThreeMenMorrisMovePayload;
import org.kevinkib.cardgames.threemenmorris.presentation.api.ThreeMenMorrisPlacePayload;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ThreeMenMorrisWebSocketControllerTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorrisTable table = new ThreeMenMorrisTable();

    private RoomCreated createRoom() {
        return table.sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
    }

    @Test
    void start_byTheHostBroadcastsTheFirstStateToBothSeats() {
        RoomCreated room = createRoom();
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads, hasSize(2));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("START"));
        ThreeMenMorrisDto state = (ThreeMenMorrisDto) first.getState();
        assertThat(state.phase(), is("PLACEMENT"));
        assertThat(state.availableActions(), is(List.of("PLACE")));
        assertThat(((ThreeMenMorrisDto) table.messaging.payloads.get(1).getState()).availableActions(),
                is(List.of()));
    }

    @Test
    void start_withOnlyOnePlayerOrByANonHost_repliesAnErrorToTheSenderOnly() {
        RoomCreated room = createRoom();
        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.seats.get(0), is(WHITE));

        table.messaging.clear();
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats, is(List.of(BLACK)));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void start_onAnotherGameTypesRoom_isRejectedAndDoesNotStartThatGame() {
        SessionService both = new SessionService(table.repository,
                new GameFactories(List.of(new ThreeMenMorrisFactory(), new BullshitFactory())));
        ThreeMenMorrisWebSocketController ws = new ThreeMenMorrisWebSocketController(both, table.broadcaster,
                new org.kevinkib.cardgames.presentation.command.SeatCommands(both, table.messaging), new ThreeMenMorrisGameActions(both, table.broadcaster));
        RoomCreated room = both.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        both.joinRoom(new GameId(room.gameId()), "Bob");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(both.findGame(new GameId(room.gameId())).isPresent(), is(false));
        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void place_andMove_areAuthenticatedByTheSeatToken() {
        ThreeMenMorris game = table.newGame();
        String gameId = game.getId().uuid().toString();

        table.ws.place(new ThreeMenMorrisPlacePayload(gameId, table.token(game, 0), 4));
        assertThat(game.owner(4), is(WHITE));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("PLACE"));

        table.ws.place(new ThreeMenMorrisPlacePayload(gameId, "not-a-token", 0));
        assertThat(game.owner(0), is(nullValue()));
        assertThat(table.messaging.payloads, hasSize(2));

        for (int[] placement : new int[][]{{1, 1}, {0, 2}, {1, 8}, {0, 7}, {1, 6}}) {
            table.ws.place(new ThreeMenMorrisPlacePayload(gameId, table.token(game, placement[0]), placement[1]));
        }
        table.messaging.clear();
        table.ws.move(new ThreeMenMorrisMovePayload(gameId, table.token(game, 0), 2, 5));
        assertThat(game.owner(5), is(WHITE));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("MOVE"));
    }

    @Test
    void aRejectedCommandRepliesToTheSenderOnlyWithItsCurrentState() {
        ThreeMenMorris game = table.newGame();
        String gameId = game.getId().uuid().toString();

        table.ws.place(new ThreeMenMorrisPlacePayload(gameId, table.token(game, 1), 4));

        assertThat(table.messaging.seats, is(List.of(BLACK)));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("PLACE"));
        assertThat(((ThreeMenMorrisDto) error.getState()).version(), is(0));
        assertThat(game.version(), is(0));
    }

    @Test
    void commandsWithAMissingFieldAreRejectedWithoutAnyEffect() {
        ThreeMenMorris game = table.newGame();
        String gameId = game.getId().uuid().toString();
        String white = table.token(game, 0);

        table.ws.place(new ThreeMenMorrisPlacePayload(gameId, white, null));
        table.ws.move(new ThreeMenMorrisMovePayload(gameId, white, null, 1));
        table.ws.move(new ThreeMenMorrisMovePayload(gameId, white, 1, null));

        assertThat(table.messaging.payloads, hasSize(3));
        assertThat(table.messaging.payloads.stream().noneMatch(Response::isSuccess), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void aRefusedStartOfALobby_carriesNoState() {
        RoomCreated room = createRoom();

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.seats, is(List.of(WHITE)));
        assertThat(table.messaging.payloads.get(0).getState(), is(nullValue()));
    }

    @Test
    void aMalformedOrUnknownGameId_isIgnoredWithoutAnyException() {
        for (String rawId : new String[]{"not-a-uuid", "", java.util.UUID.randomUUID().toString()}) {
            table.ws.place(new ThreeMenMorrisPlacePayload(rawId, "token", 0));
            table.ws.start(new GameActionPayload(rawId, "token"));
        }

        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }
}
