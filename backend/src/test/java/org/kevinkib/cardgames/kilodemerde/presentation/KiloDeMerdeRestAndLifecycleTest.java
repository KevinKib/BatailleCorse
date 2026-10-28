package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;

class KiloDeMerdeRestAndLifecycleTest {

    private static final PlayerId S1 = new PlayerId(1);

    private final KiloDeMerdeTable table = new KiloDeMerdeTable();

    @Test
    void getGameWithoutOrWithAWrongTokenIsForbidden() {
        KiloDeMerde game = table.start(NO_SET);
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
        KiloDeMerde game = table.start(NO_SET);

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 1));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        KiloDeMerdeDto dto = (KiloDeMerdeDto) response.getBody();
        assertThat(dto.gameType()).isEqualTo("kilo-de-merde");
        assertThat(dto.hand()).hasSize(4);
        assertThat(dto.availableActions()).containsExactly("PASS", "SLAP");
    }

    @Test
    void getGameBeforeTheStartReturnsTheLobbyWithTheBoundsOfTheGame() {
        RoomCreated room = table.sessions.createRoom(KiloDeMerdeFactory.GAME_TYPE, "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody()).isInstanceOf(LobbyView.class);
        assertThat(((LobbyView) response.getBody()).minPlayers()).isEqualTo(3);
        assertThat(((LobbyView) response.getBody()).maxPlayers()).isEqualTo(10);
    }

    @Test
    void joinFillsSeatsUpToTenAndIsRefusedOnceFullOrStarted() {
        RoomCreated room = table.sessions.createRoom(KiloDeMerdeFactory.GAME_TYPE, "Alice");

        for (int seat = 1; seat < 10; seat++) {
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
    void playAgainReopensTheRoomWithTheCallerAsHostAndForgetsTheFixedDeal() {
        List<GameId> forgotten = new ArrayList<>();
        KiloDeMerdeRestController rest = new KiloDeMerdeRestController(table.sessions, table.broadcaster,
                table.lobbyBroadcaster, forgotten::add);
        RoomCreated room = table.sessions.createRoom(KiloDeMerdeFactory.GAME_TYPE, "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        table.sessions.joinRoom(new GameId(room.gameId()), "Cleo");
        KiloDeMerde game = (KiloDeMerde) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(S1);
        game.forfeit(new PlayerId(2));

        ResponseEntity<JoinResponseDto> again = rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getBody().playerId()).isZero();
        assertThat(table.sessions.findGame(new GameId(room.gameId()))).isEmpty();
        assertThat(forgotten).containsExactly(new GameId(room.gameId()));
    }

    @Test
    void theLifecycleBroadcasterSupportsTheGameOnlyAndBroadcastsTheThreeEvents() {
        KiloDeMerde game = table.start(NO_SET);
        SeatLifecycleBroadcaster<KiloDeMerde> lifecycle =
                new SeatLifecycleBroadcaster<>(KiloDeMerde.class, table.broadcaster);
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
        assertThat(((KiloDeMerdeDto) forfeit.getState()).players().get(1).active()).isFalse();
    }

    @Test
    void aForfeitAnnouncesTheNewDealOrTheEndOfTheGameAfterTheForfeitItself() {
        KiloDeMerdeLifecycleBroadcaster lifecycle = new KiloDeMerdeLifecycleBroadcaster(table.broadcaster, table.actions);
        KiloDeMerde four = table.start(4, NO_SET + " / 2S 2H 2D 2C", KiloDeMerdeTable.PILE);
        four.forfeit(S1);

        lifecycle.forfeited(four, S1, ForfeitReason.RESIGNED);

        assertThat(table.messaging.payloads.stream().map(Response::getEventType).distinct().toList())
                .containsExactly("FORFEIT", "ROUND_START");

        table.messaging.clear();
        KiloDeMerde three = table.start(NO_SET);
        three.forfeit(S1);

        lifecycle.forfeited(three, S1, ForfeitReason.RESIGNED);

        assertThat(table.messaging.payloads.stream().map(Response::getEventType).distinct().toList())
                .containsExactly("FORFEIT", "GAME_OVER");
        assertThat(lifecycle.supports(three)).isTrue();
    }
}
