package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class MangolaRestAndLifecycleTest {

    private static final PlayerId SEAT_0 = MangolaTable.SEAT_0;
    private static final PlayerId SEAT_1 = MangolaTable.SEAT_1;

    private final MangolaTable table = new MangolaTable();

    // ---- REST

    @Test
    void getGame_withoutOrWithAWrongToken_isForbidden() {
        Mangola game = table.placementGame();
        String id = game.getId().uuid().toString();

        assertThat(table.rest.getGame(id, null).getStatusCode().value(), is(403));
        assertThat(table.rest.getGame(id, UUID.randomUUID().toString()).getStatusCode().value(), is(403));
    }

    @Test
    void getGame_ofAnUnknownIdOrAnotherGameType_isNotFound() {
        assertThat(table.rest.getGame("nope", "t").getStatusCode().value(), is(404));
        assertThat(table.rest.getGame(UUID.randomUUID().toString(), "t").getStatusCode().value(), is(404));
        RoomCreated other = table.sessions.createRoom("bullshit", "Alice");
        assertThat(table.rest.getGame(other.gameId(), other.hostToken()).getStatusCode().value(), is(404));
    }

    @Test
    void getGame_rehydratesTheViewersOwnDraftAndNothingOfTheOthers() throws Exception {
        Mangola game = table.placementGame();
        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        game.arrange(SEAT_1, MangolaTable.allOnFirst());

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 0));

        assertThat(response.getStatusCode().value(), is(200));
        MangolaDto dto = (MangolaDto) response.getBody();
        assertThat(dto.gameType(), is("mangola"));
        assertThat(dto.board().get(8), is(2));
        assertThat(dto.board().get(24), is((Integer) null));
        assertThat(dto.availableActions(), is(List.of("ARRANGE", "CONFIRM_LAYOUT")));
    }

    @Test
    void getGame_beforeTheStart_returnsTheLobbyWithItsOnlyOption() {
        RoomCreated room = table.sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody(), instanceOf(LobbyView.class));
        LobbyView lobby = (LobbyView) response.getBody();
        assertThat(lobby.minPlayers(), is(2));
        assertThat(lobby.maxPlayers(), is(2));
        assertThat(lobby.optionDescriptors().stream().map(d -> d.key()).toList(), is(List.of("firstPlayer")));
    }

    @Test
    void join_fillsTheSecondSeat_andIsRefusedOnceFullOrStarted() {
        RoomCreated room = table.sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice");

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
    void playAgain_reopensTheRoomWithTheCallerAsHostAndKeepsTheOptions() {
        RoomCreated room = table.sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice");
        table.sessions.updateOptions(new GameId(room.gameId()), room.hostToken(),
                Map.of("firstPlayer", "guest"));
        table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));
        Mangola game = (Mangola) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(SEAT_1);
        assertThat(game.isFinished(), is(true));

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value(), is(200));
        assertThat(again.getBody().playerId(), is(0));
        assertThat(table.sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
        LobbyView lobby = (LobbyView) table.rest.getGame(room.gameId(), again.getBody().token()).getBody();
        assertThat(lobby.options(), is(Map.of("firstPlayer", "guest")));
    }

    // ---- session core hosts the game

    @Test
    void sessionCore_startsTheGameLikeAnyOtherInPlacementWithTheDefaultOptions() {
        RoomCreated room = table.sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        table.sessions.joinRoom(id, "Bob");

        Mangola game = (Mangola) table.sessions.startGame(id, room.hostToken());

        assertThat(game.getPlayerIds().size(), is(2));
        assertThat(game.phase().name(), is("PLACEMENT"));
        // the generator of the table is constant: the host starts and owns side A
        assertThat(game.seatA(), is(SEAT_0));
        assertThat(table.sessions.minPlayers(MangolaFactory.GAME_TYPE), is(2));
        assertThat(table.sessions.maxPlayers(MangolaFactory.GAME_TYPE), is(2));
    }

    // ---- lifecycle

    @Test
    void lifecycleBroadcaster_supportsTheGameOnlyAndBroadcastsTheThreeEvents() {
        Mangola game = table.placementGame();
        SeatLifecycleBroadcaster<Mangola> lifecycle = new SeatLifecycleBroadcaster<>(Mangola.class, table.broadcaster);
        assertThat(lifecycle.supports(game), is(true));
        assertThat(lifecycle.supports(new Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        }), is(false));

        lifecycle.disconnected(game, SEAT_1, 123L);
        lifecycle.reconnected(game, SEAT_1);
        game.forfeit(SEAT_1);
        lifecycle.forfeited(game, SEAT_1, ForfeitReason.DISCONNECTED);

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).toList();
        assertThat(types, is(List.of("OPPONENT_DISCONNECTED", "OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED",
                "OPPONENT_RECONNECTED", "FORFEIT", "FORFEIT")));
        Response forfeit = table.messaging.payloads.get(4);
        assertThat(((ForfeitEventData) forfeit.getEventData()).reason(), is("DISCONNECTED"));
        assertThat(((ForfeitEventData) forfeit.getEventData()).loserSeat(), is(1));
        assertThat(((MangolaDto) forfeit.getState()).outcome(), is(new MangolaDto.Finished(0, "FORFEIT")));
    }
}
