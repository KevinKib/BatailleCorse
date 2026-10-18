package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;

class PresidentRestAndLifecycleTest {

    private final PresidentTable table = new PresidentTable();

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private President threePlayers() {
        return table.install(aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C 8C").leader(0).build());
    }

    private String startedRoom() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.sessions.joinRoom(new GameId(room.gameId()), "Carol");
        table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        return room.gameId();
    }

    // ---- REST

    @Test
    void getGame_withoutOrWithAWrongToken_isForbidden() {
        President game = threePlayers();
        String id = game.getId().uuid().toString();

        assertThat(table.rest.getGame(id, null).getStatusCode().value(), is(403));
        assertThat(table.rest.getGame(id, UUID.randomUUID().toString()).getStatusCode().value(), is(403));
    }

    @Test
    void getGame_ofAnUnknownIdOrAnotherGameType_isNotFound() {
        assertThat(table.rest.getGame("nope", "t").getStatusCode().value(), is(404));
        assertThat(table.rest.getGame(UUID.randomUUID().toString(), "t").getStatusCode().value(), is(404));
        String bullshit = table.sessions.createRoom("bullshit", "Alice").gameId();
        assertThat(table.rest.getGame(bullshit, "t").getStatusCode().value(), is(404));
    }

    @Test
    void getGame_returnsTheViewerOwnStateOnceStarted() {
        President game = threePlayers();

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 1));

        assertThat(response.getStatusCode().value(), is(200));
        PresidentDto dto = (PresidentDto) response.getBody();
        assertThat(dto.gameType(), is("president"));
        assertThat(dto.hand().size(), is(2));
        assertThat(dto.availableActions(), is(List.of()));
    }

    @Test
    void getGame_beforeTheStart_returnsTheLobbyWithTheBoundsAndTheOptions() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody(), instanceOf(LobbyView.class));
        LobbyView lobby = (LobbyView) response.getBody();
        assertThat(lobby.minPlayers(), is(3));
        assertThat(lobby.maxPlayers(), is(8));
        assertThat(lobby.options().containsKey("firstLead"), is(false));
        assertThat(lobby.options().get("length"), is("rounds-5"));
    }

    @Test
    void join_fillsTheNextSeat_andIsRefusedOnceStartedOrFull() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");

        ResponseEntity<JoinResponseDto> joined = table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(joined.getStatusCode().value(), is(200));
        assertThat(joined.getBody().playerId(), is(1));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("JOIN"));

        table.rest.joinGame(room.gameId(), new JoinGamePayload("Carol"));
        table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value(), is(409));
        assertThat(table.rest.joinGame(UUID.randomUUID().toString(), null).getStatusCode().value(), is(404));
    }

    @Test
    void playAgain_reopensTheRoomWithTheCallerAsHost() throws Exception {
        String roomId = startedRoom();
        President game = (President) table.sessions.findGame(new GameId(roomId)).orElseThrow();
        game.forfeit(seat(1));
        game.forfeit(seat(2));
        assertThat(game.isFinished(), is(true));

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(roomId, new JoinGamePayload("Alice"));

        assertThat(again.getStatusCode().value(), is(200));
        assertThat(again.getBody().playerId(), is(0));
        assertThat(table.sessions.findGame(new GameId(roomId)).isPresent(), is(false));
    }

    // ---- session core hosts President

    @Test
    void sessionCore_startsPresidentLikeAnyGameAndRefusesFewerThanThreePlayers() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");
        GameId id = new GameId(room.gameId());
        String bob = table.sessions.joinRoom(id, "Bob").token();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> table.sessions.startGame(id, room.hostToken()));
        table.sessions.joinRoom(id, "Carol");
        President game = (President) table.sessions.startGame(id, room.hostToken());

        assertThat(game.getPlayerIds().size(), is(3));
        assertThat(table.sessions.minPlayers("president"), is(3));
        assertThat(table.sessions.maxPlayers("president"), is(8));
        assertThat(bob, notNullValue());
    }

    @Test
    void theHostOptionsReachTheGame() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");
        GameId id = new GameId(room.gameId());
        table.sessions.updateOptions(id, room.hostToken(), java.util.Map.of("length", "endless", "afterPass", "out"));
        table.sessions.joinRoom(id, "Bob");
        table.sessions.joinRoom(id, "Carol");

        President game = (President) table.sessions.startGame(id, room.hostToken());

        assertThat(game.options().length().key(), is("endless"));
        assertThat(game.options().afterPass().key(), is("out"));
    }

    @Test
    void theLobbyRefusesTheOptionsThatWerePrunedAndTheValuesItDoesNotKnow() {
        RoomCreated room = table.sessions.createRoom("president", "Alice");
        GameId id = new GameId(room.gameId());

        org.junit.jupiter.api.Assertions.assertThrows(org.kevinkib.cardgames.game.InvalidOptionException.class,
                () -> table.sessions.updateOptions(id, room.hostToken(), java.util.Map.of("jokers", "wild")));
        org.junit.jupiter.api.Assertions.assertThrows(org.kevinkib.cardgames.game.InvalidOptionException.class,
                () -> table.sessions.updateOptions(id, room.hostToken(), java.util.Map.of("length", "rounds-4")));
    }

    // ---- lifecycle

    @Test
    void lifecycleBroadcaster_forfeitCarriesTheReason() {
        President game = threePlayers();
        SeatLifecycleBroadcaster<President> lifecycle =
                new SeatLifecycleBroadcaster<>(President.class, table.broadcaster);

        game.forfeit(seat(2));
        lifecycle.forfeited(game, seat(2), ForfeitReason.RESIGNED);

        Response resigned = table.messaging.payloads.get(0);
        assertThat(resigned.getEventType(), is("FORFEIT"));
        assertThat(((ForfeitEventData) resigned.getEventData()).reason(), is("RESIGNED"));
        assertThat(((ForfeitEventData) resigned.getEventData()).loserSeat(), is(2));
        // the game goes on with the two others, who are the only ones still receiving states
        assertThat(table.messaging.seats, is(List.of(seat(0), seat(1))));
        assertThat(((PresidentDto) resigned.getState()).players().size(), is(2));
    }

    @Test
    void lifecycleBroadcaster_supportsPresidentOnlyAndBroadcastsTheThreeEvents() {
        President game = threePlayers();
        SeatLifecycleBroadcaster<President> lifecycle =
                new SeatLifecycleBroadcaster<>(President.class, table.broadcaster);
        assertThat(lifecycle.supports(game), is(true));
        assertThat(lifecycle.supports(new org.kevinkib.cardgames.game.Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        }), is(false));

        lifecycle.disconnected(game, seat(1), 123L);
        lifecycle.reconnected(game, seat(1));

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).toList();
        assertThat(types, is(List.of("OPPONENT_DISCONNECTED", "OPPONENT_DISCONNECTED", "OPPONENT_DISCONNECTED",
                "OPPONENT_RECONNECTED", "OPPONENT_RECONNECTED", "OPPONENT_RECONNECTED")));
    }
}
