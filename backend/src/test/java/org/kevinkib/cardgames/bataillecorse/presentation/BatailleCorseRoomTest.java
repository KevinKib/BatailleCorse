package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/** Bataille Corse on the room model: lobby, join, start and the game-type guard, without a server. */
class BatailleCorseRoomTest {

    private static final class Recorder extends org.kevinkib.cardgames.presentation.TokenResolvingMessaging {
        final List<PlayerId> seats = Collections.synchronizedList(new ArrayList<>());
        final List<Response> toSeats = Collections.synchronizedList(new ArrayList<>());
        final List<Response> toGame = Collections.synchronizedList(new ArrayList<>());

        Recorder() {  }


        @Override public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            toSeats.add((Response) payload);
        }

        @Override public void sendToGame(String gameId, Object payload) { toGame.add((Response) payload); }
    }

    private final SessionService sessions = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()),
            new GameFactories(List.of(new BatailleCorseFactory(), new BullshitFactory())));
    private final Recorder messaging = new Recorder();
    { messaging.resolver = sessions; }
    private final BatailleCorseRestController rest = new BatailleCorseRestController(
            sessions, messaging, new InMemoryForfeitLog(), new LobbyBroadcaster(messaging));
    private final BatailleCorseWebSocketController ws = new BatailleCorseWebSocketController(sessions, messaging);

    private RoomCreated room() {
        return sessions.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");
    }

    private ResponseEntity<?> get(String id, String token) {
        return rest.getGameForSeat(id, token);
    }

    @Test
    void getWithAToken_returnsTheLobbyViewWhileTheRoomIsNotStarted() {
        RoomCreated room = room();

        ResponseEntity<?> response = get(room.gameId(), room.hostToken());

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        LobbyView view = (LobbyView) response.getBody();
        assertThat(view.canStart(), is(false));
        assertThat(view.maxPlayers(), is(2));
        assertThat(view.mySeat(), is(0));
    }

    @Test
    void getWithAnInvalidToken_isForbidden() {
        RoomCreated room = room();

        assertThat(get(room.gameId(), "nope").getStatusCode(), is(HttpStatus.FORBIDDEN));
        assertThat(get(room.gameId(), UUID.randomUUID().toString()).getStatusCode(), is(HttpStatus.FORBIDDEN));
    }

    @Test
    void getWithAToken_onAnUnknownIdOrAnotherGameType_is404() {
        RoomCreated other = sessions.createRoom(BullshitFactory.GAME_TYPE, "Bob");

        assertThat(get("unknown-id", "t").getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(get(other.gameId(), other.hostToken()).getStatusCode(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void join_fillsTheSecondSeatAndBroadcastsTheLobbyToBothSeats() {
        RoomCreated room = room();

        ResponseEntity<JoinResponseDto> joined = rest.joinGame(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(joined.getStatusCode(), is(HttpStatus.OK));
        assertThat(joined.getBody().playerId(), is(1));
        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        assertThat(messaging.toGame, empty());
        LobbyView hostView = (LobbyView) messaging.toSeats.get(0).getState();
        assertThat(hostView.canStart(), is(true));
        assertThat(hostView.players().get(1).name(), is("Bob"));
        LobbyView guestView = (LobbyView) get(room.gameId(), joined.getBody().token()).getBody();
        assertThat(guestView.canStart(), is(false));
        assertThat(guestView.mySeat(), is(1));
    }

    @Test
    void aThirdJoin_isRefused() {
        RoomCreated room = room();
        rest.joinGame(room.gameId(), null);

        assertThat(rest.joinGame(room.gameId(), null).getStatusCode(), is(HttpStatus.CONFLICT));
    }

    @Test
    void start_dealsTwoPlayersOnceTwoSeatsAreTaken_andAJoinAfterwardsIsRefused() {
        RoomCreated room = room();
        String guestToken = rest.joinGame(room.gameId(), null).getBody().token();

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        BatailleCorse game = sessions.getGame(new GameId(room.gameId()), BatailleCorse.class);
        assertThat(game.getPlayers(), hasSize(2));
        assertThat(messaging.toGame, hasSize(1));
        assertThat(messaging.toGame.get(0).getEventType(), is("START"));
        assertThat(rest.joinGame(room.gameId(), null).getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(get(room.gameId(), guestToken).getBody(), instanceOf(BatailleCorseDto.class));
        assertThat(get(room.gameId(), "nope").getStatusCode(), is(HttpStatus.FORBIDDEN));
    }

    @Test
    void start_withOneSeatOrByAGuest_repliesAnErrorToTheSenderOnly() {
        RoomCreated room = room();
        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
        assertThat(messaging.toSeats.get(0).isSuccess(), is(false));
        assertThat(messaging.seats.get(0), is(new PlayerId(0)));

        messaging.seats.clear();
        messaging.toSeats.clear();
        String guestToken = sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(sessions.findGame(new GameId(room.gameId())).isPresent(), is(false));
        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        assertThat(messaging.toGame, empty());
    }

    @Test
    void start_onAnotherGameTypesRoom_doesNotStartIt() {
        RoomCreated other = sessions.createRoom(BullshitFactory.GAME_TYPE, "Bob");
        sessions.joinRoom(new GameId(other.gameId()), "Carol");

        ws.start(new GameActionPayload(other.gameId(), other.hostToken()));

        assertThat(sessions.findGame(new GameId(other.gameId())).isPresent(), is(false));
        assertThat(messaging.toSeats.get(0).isSuccess(), is(false));
    }

    @Test
    void join_onAnotherGameTypesIdOrAnUnknownId_is404() {
        RoomCreated other = sessions.createRoom(BullshitFactory.GAME_TYPE, "Bob");

        assertThat(rest.joinGame(other.gameId(), null).getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(rest.joinGame("unknown-id", null).getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(sessions.seats(new GameId(other.gameId())).get(1).joined(), is(false));
    }

    @Test
    void legacyGetWithoutToken_onAnotherGameTypesId_is404() {
        RoomCreated other = sessions.createRoom(BullshitFactory.GAME_TYPE, "Bob");
        sessions.joinRoom(new GameId(other.gameId()), "Carol");
        sessions.startGame(new GameId(other.gameId()), other.hostToken());

        assertThat(rest.getGame(other.gameId()).getStatusCode(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void aBotSeat_isFilledAtServiceLevel_andStartDealsTwoPlayers() {
        RoomCreated room = room();
        GameId id = new GameId(room.gameId());

        PlayerId bot = sessions.addBot(id, room.hostToken());

        assertThat(bot, is(new PlayerId(1)));
        assertThat(sessions.botSeats(id), is(Set.of(new PlayerId(1))));
        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(sessions.getGame(id, BatailleCorse.class).getPlayers(), hasSize(2));
    }
}
