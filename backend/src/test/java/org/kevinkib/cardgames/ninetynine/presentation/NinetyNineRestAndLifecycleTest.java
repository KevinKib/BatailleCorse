package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
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

import static org.assertj.core.api.Assertions.assertThat;

class NinetyNineRestAndLifecycleTest {

    private static final PlayerId S1 = new PlayerId(1);

    private final NinetyNineTable table = new NinetyNineTable();

    @Test
    void getGameWithoutOrWithAWrongTokenIsForbidden() {
        NinetyNine game = table.start(2, "KS 7H 9S 8H 4S 2H");
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
        NinetyNine game = table.start(2, "KS 7H 9S 8H 4S 2H");

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 1));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        NinetyNineDto dto = (NinetyNineDto) response.getBody();
        assertThat(dto.gameType()).isEqualTo("ninety-nine");
        assertThat(dto.hand()).hasSize(3);
        assertThat(dto.availableActions()).isEmpty();
    }

    @Test
    void getGameBeforeTheStartReturnsTheLobbyWithTheBoundsOfTheGame() {
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody()).isInstanceOf(LobbyView.class);
        assertThat(((LobbyView) response.getBody()).minPlayers()).isEqualTo(2);
        assertThat(((LobbyView) response.getBody()).maxPlayers()).isEqualTo(12);
    }

    @Test
    void joinFillsSeatsUpToTwelveAndIsRefusedOnceFullOrStarted() {
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");

        for (int seat = 1; seat < 12; seat++) {
            ResponseEntity<JoinResponseDto> joined = table.rest.joinGame(room.gameId(), new JoinGamePayload("P" + seat));
            assertThat(joined.getStatusCode().value()).isEqualTo(200);
            assertThat(joined.getBody().playerId()).isEqualTo(seat);
        }
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value()).isEqualTo(409);

        table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value()).isEqualTo(409);
        assertThat(table.rest.joinGame(UUID.randomUUID().toString(), null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void playAgainReopensTheRoomWithTheCallerAsHost() {
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");
        table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));
        NinetyNine game = (NinetyNine) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(S1);

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getBody().playerId()).isZero();
        assertThat(table.sessions.findGame(new GameId(room.gameId()))).isEmpty();
    }

    @Test
    void playAgainForgetsTheDealFixedForTheFinishedGame() {
        java.util.List<GameId> forgotten = new java.util.ArrayList<>();
        NinetyNineRestController rest = new NinetyNineRestController(table.sessions, table.broadcaster,
                table.lobbyBroadcaster, forgotten::add);
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        NinetyNine game = (NinetyNine) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(S1);

        rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(forgotten).containsExactly(new GameId(room.gameId()));
    }

    @Test
    void theSessionCoreHostsTheGameLikeAnyOther() {
        RoomCreated room = table.sessions.createRoom(NinetyNineFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        table.sessions.joinRoom(id, "Bob");

        NinetyNine game = (NinetyNine) table.sessions.startGame(id, room.hostToken());

        assertThat(game.getPlayerIds()).hasSize(2);
        assertThat(table.sessions.minPlayers(NinetyNineFactory.GAME_TYPE)).isEqualTo(2);
        assertThat(table.sessions.maxPlayers(NinetyNineFactory.GAME_TYPE)).isEqualTo(12);
    }

    @Test
    void theLifecycleBroadcasterSupportsTheGameOnlyAndBroadcastsTheThreeEvents() {
        NinetyNine game = table.start(3, "KS 7H JC 9S 8H QC 4S 2H 3C");
        SeatLifecycleBroadcaster<NinetyNine> lifecycle =
                new SeatLifecycleBroadcaster<>(NinetyNine.class, table.broadcaster);
        assertThat(lifecycle.supports(game)).isTrue();
        assertThat(lifecycle.supports(new Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        })).isFalse();

        lifecycle.disconnected(game, S1, 123L);
        lifecycle.reconnected(game, S1);
        game.forfeit(S1);
        lifecycle.forfeited(game, S1, ForfeitReason.DISCONNECTED);

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).distinct().toList();
        assertThat(types).containsExactly("OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED", "FORFEIT");
        Response forfeit = table.messaging.payloads.get(table.messaging.payloads.size() - 1);
        assertThat(((ForfeitEventData) forfeit.getEventData()).reason()).isEqualTo("DISCONNECTED");
        assertThat(((ForfeitEventData) forfeit.getEventData()).loserSeat()).isEqualTo(1);
        NinetyNineDto state = (NinetyNineDto) forfeit.getState();
        assertThat(state.players().get(1).eliminated()).isTrue();
        assertThat(state.outcome()).isEqualTo(new NinetyNineDto.OutcomeDto.Ongoing());
    }
}
