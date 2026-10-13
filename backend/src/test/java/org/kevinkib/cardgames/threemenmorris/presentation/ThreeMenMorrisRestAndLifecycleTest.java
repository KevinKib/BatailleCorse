package org.kevinkib.cardgames.threemenmorris.presentation;

import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class ThreeMenMorrisRestAndLifecycleTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorrisTable table = new ThreeMenMorrisTable();

    // ---- REST

    @Test
    void getGame_withoutOrWithAWrongToken_isForbidden() {
        ThreeMenMorris game = table.newGame();
        String id = game.getId().uuid().toString();

        assertThat(table.rest.getGame(id, null).getStatusCode().value(), is(403));
        assertThat(table.rest.getGame(id, UUID.randomUUID().toString()).getStatusCode().value(), is(403));
    }

    @Test
    void getGame_ofAnUnknownIdOrAnotherGameType_isNotFound() {
        assertThat(table.rest.getGame("nope", "t").getStatusCode().value(), is(404));
        assertThat(table.rest.getGame(UUID.randomUUID().toString(), "t").getStatusCode().value(), is(404));
    }

    @Test
    void getGame_returnsTheViewerOwnStateOnceStarted() {
        ThreeMenMorris game = table.newGame();

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 0));

        assertThat(response.getStatusCode().value(), is(200));
        ThreeMenMorrisDto dto = (ThreeMenMorrisDto) response.getBody();
        assertThat(dto.gameType(), is("three-men-morris"));
        assertThat(dto.availableActions(), is(List.of("PLACE")));
    }

    @Test
    void getGame_beforeTheStart_returnsTheLobby() {
        RoomCreated room = table.sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody(), instanceOf(LobbyView.class));
        assertThat(((LobbyView) response.getBody()).minPlayers(), is(2));
        assertThat(((LobbyView) response.getBody()).maxPlayers(), is(2));
    }

    @Test
    void join_fillsTheSecondSeat_andIsRefusedOnceFullOrStarted() {
        RoomCreated room = table.sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");

        ResponseEntity<JoinResponseDto> joined = table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(joined.getStatusCode().value(), is(200));
        assertThat(joined.getBody().playerId(), is(1));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("JOIN"));
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value(), is(409));

        table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value(), is(409));
        assertThat(table.rest.joinGame(UUID.randomUUID().toString(), null).getStatusCode().value(), is(404));
    }

    @Test
    void playAgain_reopensTheRoomWithTheCallerAsHost() {
        RoomCreated room = table.sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));
        ThreeMenMorris game = (ThreeMenMorris) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(BLACK);
        assertThat(game.isFinished(), is(true));

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value(), is(200));
        assertThat(again.getBody().playerId(), is(0));
        assertThat(table.sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
    }

    // ---- session core hosts the game

    @Test
    void sessionCore_startsTheGameLikeAnyOther() {
        RoomCreated room = table.sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        table.sessions.joinRoom(id, "Bob");

        ThreeMenMorris game = (ThreeMenMorris) table.sessions.startGame(id, room.hostToken());

        assertThat(game.getPlayerIds().size(), is(2));
        assertThat(table.sessions.minPlayers(ThreeMenMorrisFactory.GAME_TYPE), is(2));
        assertThat(table.sessions.maxPlayers(ThreeMenMorrisFactory.GAME_TYPE), is(2));
    }

    // ---- lifecycle

    @Test
    void lifecycleBroadcaster_supportsTheGameOnlyAndBroadcastsTheThreeEvents() {
        ThreeMenMorris game = table.newGame();
        SeatLifecycleBroadcaster<ThreeMenMorris> lifecycle =
                new SeatLifecycleBroadcaster<>(ThreeMenMorris.class, table.broadcaster);
        assertThat(lifecycle.supports(game), is(true));
        assertThat(lifecycle.supports(new Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        }), is(false));

        lifecycle.disconnected(game, BLACK, 123L);
        lifecycle.reconnected(game, BLACK);
        game.forfeit(BLACK);
        lifecycle.forfeited(game, BLACK, ForfeitReason.DISCONNECTED);

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).toList();
        assertThat(types, is(List.of("OPPONENT_DISCONNECTED", "OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED",
                "OPPONENT_RECONNECTED", "FORFEIT", "FORFEIT")));
        Response forfeit = table.messaging.payloads.get(4);
        assertThat(((ForfeitEventData) forfeit.getEventData()).reason(), is("DISCONNECTED"));
        assertThat(((ForfeitEventData) forfeit.getEventData()).loserSeat(), is(1));
        assertThat(((ThreeMenMorrisDto) forfeit.getState()).outcome(),
                is(new ThreeMenMorrisDto.Finished(0, "FORFEIT")));
    }
}
