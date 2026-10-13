package org.kevinkib.cardgames.presentation.rest;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class SeatGameRestTest {

    private static final String TYPE = FakeGameFactory.GAME_TYPE;

    private final SessionTable table = new SessionTable(new FakeGameFactory());
    private final SeatGameRest rest = new SeatGameRest(table.sessions, new LobbyBroadcaster(table.messaging));

    private int status(ResponseEntity<?> response) {
        return response.getStatusCode().value();
    }

    @Test
    void stateIsForbiddenWithoutATokenOrWithAnUnknownOne() {
        FakeGame game = table.install(new FakeGame(GameId.generate(), 2), TYPE);
        String id = game.getId().uuid().toString();

        assertThat(status(rest.state(TYPE, id, null, (g, seat) -> "state")), is(403));
        assertThat(status(rest.state(TYPE, id, UUID.randomUUID().toString(), (g, seat) -> "state")), is(403));
    }

    @Test
    void stateIsNotFoundForAMalformedIdAnUnknownIdOrAnotherGameType() {
        FakeGame game = table.install(new FakeGame(GameId.generate(), 2), TYPE);

        assertThat(status(rest.state(TYPE, "nope", "t", (g, seat) -> "state")), is(404));
        assertThat(status(rest.state(TYPE, UUID.randomUUID().toString(), "t", (g, seat) -> "state")), is(404));
        assertThat(status(rest.state("other", game.getId().uuid().toString(), table.token(game, 0),
                (g, seat) -> "state")), is(404));
    }

    @Test
    void stateOfARunningGameIsBuiltForTheViewerSeat() {
        FakeGame game = table.install(new FakeGame(GameId.generate(), 2), TYPE);

        ResponseEntity<?> response = rest.state(TYPE, game.getId().uuid().toString(), table.token(game, 1),
                (g, seat) -> "state of " + seat.id() + " in " + (g == game));

        assertThat(status(response), is(200));
        assertThat(response.getBody(), is("state of 1 in true"));
    }

    @Test
    void stateBeforeTheStartIsTheLobbyView() {
        RoomCreated room = table.sessions.createRoom(TYPE, "Alice");

        ResponseEntity<?> response = rest.state(TYPE, room.gameId(), room.hostToken(), (g, seat) -> "state");

        assertThat(status(response), is(200));
        assertThat(response.getBody(), instanceOf(LobbyView.class));
    }

    @Test
    void joinClaimsTheNextSeatTouchesAndBroadcastsTheLobby() {
        RoomCreated room = table.sessions.createRoom(TYPE, "Alice");

        ResponseEntity<JoinResponseDto> joined = rest.join(TYPE, room.gameId(), new JoinGamePayload("Bob"));

        assertThat(status(joined), is(200));
        assertThat(joined.getBody().playerId(), is(1));
        assertThat(table.messaging.payloads.size(), is(2));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("JOIN"));
        assertThat(table.messaging.payloads.get(0).getMessage(), is("Player 2 joined."));
    }

    @Test
    void joinWithoutAPayloadStillClaimsASeat() {
        RoomCreated room = table.sessions.createRoom(TYPE, "Alice");

        assertThat(status(rest.join(TYPE, room.gameId(), null)), is(200));
    }

    @Test
    void joinIsNotFoundForAMalformedUnknownOrOtherTypeIdAndBroadcastsNothing() {
        RoomCreated room = table.sessions.createRoom(TYPE, "Alice");

        assertThat(status(rest.join(TYPE, "nope", null)), is(404));
        assertThat(status(rest.join(TYPE, UUID.randomUUID().toString(), null)), is(404));
        assertThat(status(rest.join("other", room.gameId(), null)), is(404));
        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }

    @Test
    void joinIsConflictOnceStartedOrFull() {
        RoomCreated full = table.sessions.createRoom(TYPE, "Alice");
        for (int i = 1; i < 4; i++) {
            assertThat(status(rest.join(TYPE, full.gameId(), null)), is(200));
        }
        assertThat(status(rest.join(TYPE, full.gameId(), null)), is(409));

        RoomCreated started = table.sessions.createRoom(TYPE, "Alice");
        rest.join(TYPE, started.gameId(), null);
        table.sessions.startGame(new GameId(started.gameId()), started.hostToken());
        assertThat(status(rest.join(TYPE, started.gameId(), null)), is(409));
    }

    @Test
    void joinConflictSaysWhetherTheRoomIsFullOrStarted() {
        RoomCreated full = table.sessions.createRoom(TYPE, "Alice");
        for (int i = 1; i < 4; i++) {
            rest.join(TYPE, full.gameId(), null);
        }
        assertThat(rest.join(TYPE, full.gameId(), null).getHeaders().getFirst("X-Join-Refusal"), is("full"));

        RoomCreated started = table.sessions.createRoom(TYPE, "Alice");
        rest.join(TYPE, started.gameId(), null);
        table.sessions.startGame(new GameId(started.gameId()), started.hostToken());
        assertThat(rest.join(TYPE, started.gameId(), null).getHeaders().getFirst("X-Join-Refusal"), is("started"));
    }

    @Test
    void playAgainReopensAFinishedGameWithTheCallerAsHostAndBroadcasts() {
        RoomCreated room = table.sessions.createRoom(TYPE, "Alice");
        rest.join(TYPE, room.gameId(), new JoinGamePayload("Bob"));
        FakeGame game = (FakeGame) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.finish();
        table.messaging.clear();

        ResponseEntity<JoinResponseDto> again = rest.playAgain(TYPE, room.gameId(), new JoinGamePayload("Bob"));

        assertThat(status(again), is(200));
        assertThat(again.getBody().playerId(), is(0));
        assertThat(table.sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("JOIN"));
        assertThat(table.messaging.payloads.get(0).getMessage(), is("Player 1 joined."));
    }

    @Test
    void playAgainIsNotFoundForAMalformedUnknownOrOtherTypeId() {
        RoomCreated room = table.sessions.createRoom(TYPE, "Alice");

        assertThat(status(rest.playAgain(TYPE, "nope", null)), is(404));
        assertThat(status(rest.playAgain(TYPE, UUID.randomUUID().toString(), null)), is(404));
        assertThat(status(rest.playAgain("other", room.gameId(), null)), is(404));
    }

    @Test
    void playAgainIsConflictOnAFullLobbyButNotOnAStartedRoom() {
        RoomCreated full = table.sessions.createRoom(TYPE, "Alice");
        for (int i = 1; i < 4; i++) {
            rest.join(TYPE, full.gameId(), null);
        }
        assertThat(status(rest.playAgain(TYPE, full.gameId(), null)), is(409));
    }
}
