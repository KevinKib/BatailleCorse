package org.kevinkib.cardgames.kobo.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.Phase;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
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
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;

class KoboRestAndLifecycleTest {

    private final KoboTable table = new KoboTable();

    // ---- REST

    @Test
    void getGame_withoutOrWithAWrongToken_isForbidden() {
        Kobo game = table.install(aKobo().withPlayers(2).build());
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
        Kobo game = table.install(aKobo().withPlayers(2).build());

        ResponseEntity<?> response = table.rest.getGame(game.getId().uuid().toString(), table.token(game, 1));

        assertThat(response.getStatusCode().value(), is(200));
        KoboDto dto = (KoboDto) response.getBody();
        assertThat(dto.gameType(), is("kobo"));
        assertThat(dto.availableActions(), is(List.of()));
    }

    @Test
    void getGame_beforeTheStart_returnsTheLobby() {
        RoomCreated room = table.sessions.createRoom("kobo", "Alice");

        ResponseEntity<?> response = table.rest.getGame(room.gameId(), room.hostToken());

        assertThat(response.getBody(), instanceOf(LobbyView.class));
        assertThat(((LobbyView) response.getBody()).maxPlayers(), is(6));
        assertThat(((LobbyView) response.getBody()).minPlayers(), is(2));
    }

    @Test
    void join_fillsTheNextSeat_andIsRefusedOnceStartedOrFull() {
        RoomCreated room = table.sessions.createRoom("kobo", "Alice");

        ResponseEntity<JoinResponseDto> joined = table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(joined.getStatusCode().value(), is(200));
        assertThat(joined.getBody().playerId(), is(1));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("JOIN"));

        table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        assertThat(table.rest.joinGame(room.gameId(), new JoinGamePayload("Eve")).getStatusCode().value(), is(409));
        assertThat(table.rest.joinGame(UUID.randomUUID().toString(), null).getStatusCode().value(), is(404));
    }

    @Test
    void playAgain_reopensTheRoomWithTheCallerAsHost() throws Exception {
        RoomCreated room = table.sessions.createRoom("kobo", "Alice");
        table.rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));
        Kobo game = (Kobo) table.sessions.startGame(new GameId(room.gameId()), room.hostToken());
        game.forfeit(seat(1));
        assertThat(game.isFinished(), is(true));

        ResponseEntity<JoinResponseDto> again = table.rest.playAgain(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(again.getStatusCode().value(), is(200));
        assertThat(again.getBody().playerId(), is(0));
        assertThat(table.sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
    }

    // ---- session core hosts Kobo

    @Test
    void sessionCore_startsAndRematchesKoboLikeAnyGame() {
        RoomCreated room = table.sessions.createRoom("kobo", "Alice");
        GameId id = new GameId(room.gameId());
        String bob = table.sessions.joinRoom(id, "Bob").token();

        Kobo game = (Kobo) table.sessions.startGame(id, room.hostToken());

        assertThat(game.getPlayerIds().size(), is(2));
        assertThat(table.sessions.minPlayers("kobo"), is(2));
        assertThat(table.sessions.maxPlayers("kobo"), is(6));
        assertThat(bob, notNullValue());

        game.forfeit(seat(1));
        assertThat(table.sessions.requestRematch(id, seat(0)), is(false));
        assertThat(table.sessions.requestRematch(id, seat(1)), is(true));
        Kobo fresh = (Kobo) table.sessions.rematch(id);
        assertThat(fresh.isFinished(), is(false));
        assertThat(fresh.getPlayerIds().size(), is(2));
    }

    // ---- lifecycle

    @Test
    void lifecycleBroadcaster_supportsKoboOnlyAndBroadcastsTheThreeEvents() {
        Kobo game = table.install(aKobo().withPlayers(3).build());
        KoboLifecycleBroadcaster lifecycle = new KoboLifecycleBroadcaster(table.broadcaster);
        assertThat(lifecycle.supports(game), is(true));
        assertThat(lifecycle.supports(new org.kevinkib.cardgames.game.Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        }), is(false));

        lifecycle.disconnected(game, seat(1), 123L);
        lifecycle.reconnected(game, seat(1));
        game.forfeit(seat(2));
        lifecycle.forfeited(game, seat(2), ForfeitReason.DISCONNECTED);

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).toList();
        assertThat(types.subList(0, 3), is(List.of("OPPONENT_DISCONNECTED", "OPPONENT_DISCONNECTED",
                "OPPONENT_DISCONNECTED")));
        assertThat(types.get(3), is("OPPONENT_RECONNECTED"));
        assertThat(types.get(types.size() - 1), is("FORFEIT"));
        assertThat(((KoboDto) table.messaging.payloads.get(0).getState()).phase(), is(Phase.DRAW.name()));
        assertThat(table.messaging.payloads.size(), is(3 + 3 + 2));
    }
}
