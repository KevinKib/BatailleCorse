package org.kevinkib.cardgames.draughts.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.springframework.http.ResponseEntity;

class DraughtsRestAndLifecycleTest {

    private final DraughtsTable table = new DraughtsTable();

    @Test
    void getGameWithoutOrWithAWrongTokenIsForbidden() {
        Draughts game = table.newGame();
        String id = game.getId().uuid().toString();

        assertThat(table.rest.getGame(id, null).getStatusCode().value()).isEqualTo(403);
        assertThat(table.rest.getGame(id, UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void getGameOfAnUnknownIdIsNotFound() {
        assertThat(table.rest.getGame("nope", "t").getStatusCode().value()).isEqualTo(404);
        assertThat(table.rest.getGame(UUID.randomUUID().toString(), "t").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getGameReturnsTheViewerOwnStateOnceStarted() {
        Draughts game = table.newGame();

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 0));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        DraughtsDto dto = (DraughtsDto) response.getBody();
        assertThat(dto.gameType()).isEqualTo("draughts");
        assertThat(dto.availableActions()).containsExactly("MOVE", "OFFER_DRAW");
        assertThat(dto.legalMoves()).hasSize(9);
    }

    @Test
    void getGameBeforeTheStartReturnsTheLobby() {
        RoomCreated room = table.sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody()).isInstanceOf(LobbyView.class);
        assertThat(((LobbyView) response.getBody()).minPlayers()).isEqualTo(2);
        assertThat(((LobbyView) response.getBody()).maxPlayers()).isEqualTo(2);
    }

    @Test
    void joinFillsTheSecondSeatAndIsRefusedOnceFullOrStarted() {
        RoomCreated room = table.sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice");

        ResponseEntity<JoinResponseDto> joined = table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(joined.getStatusCode().value()).isEqualTo(200);
        assertThat(joined.getBody().playerId()).isEqualTo(1);
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("JOIN");
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value()).isEqualTo(409);

        table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value()).isEqualTo(409);
        assertThat(table.rest.joinGame(UUID.randomUUID().toString(), null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void playAgainReopensTheRoomWithTheCallerAsHost() {
        RoomCreated room = table.sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice");
        table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));
        Draughts game = (Draughts) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(BLACK);

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getBody().playerId()).isZero();
        assertThat(table.sessions.findGame(new GameId(room.gameId()))).isEmpty();
    }

    @Test
    void theSessionCoreHostsTheGameLikeAnyOther() {
        RoomCreated room = table.sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        table.sessions.joinRoom(id, "Bob");

        Draughts game = (Draughts) table.sessions.startGame(id, room.hostToken());

        assertThat(game.getPlayerIds()).hasSize(2);
        assertThat(table.sessions.minPlayers(DraughtsFactory.GAME_TYPE)).isEqualTo(2);
        assertThat(table.sessions.maxPlayers(DraughtsFactory.GAME_TYPE)).isEqualTo(2);
    }

    @Test
    void theLifecycleBroadcasterSupportsTheGameOnlyAndBroadcastsTheThreeEvents() {
        Draughts game = table.newGame();
        SeatLifecycleBroadcaster<Draughts> lifecycle = new SeatLifecycleBroadcaster<>(Draughts.class, table.broadcaster);
        assertThat(lifecycle.supports(game)).isTrue();
        assertThat(lifecycle.supports(new Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        })).isFalse();

        lifecycle.disconnected(game, BLACK, 123L);
        lifecycle.reconnected(game, BLACK);
        game.forfeit(BLACK);
        lifecycle.forfeited(game, BLACK, ForfeitReason.DISCONNECTED);

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).toList();
        assertThat(types).containsExactly("OPPONENT_DISCONNECTED", "OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED",
                "OPPONENT_RECONNECTED", "FORFEIT", "FORFEIT");
        Response forfeit = table.messaging.payloads.get(4);
        assertThat(((ForfeitEventData) forfeit.getEventData()).reason()).isEqualTo("DISCONNECTED");
        assertThat(((ForfeitEventData) forfeit.getEventData()).loserSeat()).isEqualTo(1);
        assertThat(((DraughtsDto) forfeit.getState()).outcome()).isEqualTo(new DraughtsDto.Finished(0, "FORFEIT"));
    }
}
