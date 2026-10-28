package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlackjackRestAndLifecycleTest {

    private static final PlayerId S1 = new PlayerId(1);
    private static final String HAND_1 = "10S 9H 6D 7C 8C 5H 10D 3H";

    private final BlackjackTable table = new BlackjackTable();

    @Test
    void getGameWithoutOrWithAWrongTokenIsForbidden() {
        Blackjack game = table.start(2, HAND_1);
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
        Blackjack game = table.start(2, HAND_1);

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 1));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        BlackjackDto dto = (BlackjackDto) response.getBody();
        assertThat(dto.gameType()).isEqualTo("blackjack");
        assertThat(dto.availableActions()).containsExactly("BET");
    }

    @Test
    void getGameBeforeTheStartReturnsTheLobbyWithOneToSixSeats() {
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody()).isInstanceOf(LobbyView.class);
        assertThat(((LobbyView) response.getBody()).minPlayers()).isEqualTo(1);
        assertThat(((LobbyView) response.getBody()).maxPlayers()).isEqualTo(6);
    }

    @Test
    void joinFillsSeatsUpToSixAndIsRefusedOnceFullOrStarted() {
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");

        for (int seat = 1; seat < 6; seat++) {
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
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");
        table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));
        Blackjack game = (Blackjack) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(S1);

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getBody().playerId()).isZero();
        assertThat(table.sessions.findGame(new GameId(room.gameId()))).isEmpty();
    }

    @Test
    void playAgainForgetsTheShoeFixedForTheFinishedGame() {
        List<GameId> forgotten = new ArrayList<>();
        BlackjackRestController rest = new BlackjackRestController(table.sessions, table.broadcaster,
                table.lobbyBroadcaster, forgotten::add);
        RoomCreated room = table.sessions.createRoom(BlackjackFactory.GAME_TYPE, "Alice");
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");
        Blackjack game = (Blackjack) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(S1);

        rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(forgotten).containsExactly(new GameId(room.gameId()));
    }

    @Test
    void theSessionCoreHostsTheGameLikeAnyOther() {
        assertThat(table.sessions.minPlayers(BlackjackFactory.GAME_TYPE)).isEqualTo(1);
        assertThat(table.sessions.maxPlayers(BlackjackFactory.GAME_TYPE)).isEqualTo(6);
    }

    @Test
    void theLifecycleBroadcasterAnnouncesDisconnectReconnectAndForfeitWithTheAftermath() {
        Blackjack game = table.start(2, HAND_1);
        BlackjackLifecycleBroadcaster lifecycle = new BlackjackLifecycleBroadcaster(table.broadcaster, table.actions);
        assertThat(lifecycle.supports(game)).isTrue();

        lifecycle.disconnected(game, S1, 123L);
        lifecycle.reconnected(game, S1);
        game.forfeit(S1);
        lifecycle.forfeited(game, S1, ForfeitReason.DISCONNECTED);

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).distinct().toList();
        assertThat(types).containsExactly("OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED", "FORFEIT", "GAME_OVER");
        Response forfeit = table.messaging.payloads.stream().filter(p -> p.getEventType().equals("FORFEIT"))
                .findFirst().orElseThrow();
        assertThat(((ForfeitEventData) forfeit.getEventData()).loserSeat()).isEqualTo(1);
        assertThat(((BlackjackDto) forfeit.getState()).players().get(1).forfeited()).isTrue();
    }
}
