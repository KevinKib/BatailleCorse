package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class BullshitRestControllerTest {

    private SessionService sessionService;
    private BullshitRestController controller;
    private RecordingMessaging messaging;

    static final class RecordingMessaging extends org.kevinkib.cardgames.presentation.GameMessagingService {
        final java.util.List<Integer> seats = new java.util.ArrayList<>();
        final java.util.List<String> eventTypes = new java.util.ArrayList<>();
        RecordingMessaging() { super(null, null); }
        @Override
        public void sendToSeat(org.kevinkib.cardgames.game.GameId gameId,
                               org.kevinkib.cardgames.game.PlayerId seat, Object payload) {
            seats.add(seat.id());
            eventTypes.add(((org.kevinkib.cardgames.presentation.api.Response) payload).getEventType());
        }
    }

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        messaging = new RecordingMessaging();
        controller = new BullshitRestController(
                sessionService,
                new BullshitStateBroadcaster(messaging),
                new LobbyBroadcaster(messaging, sessionService));
    }

    @Test
    void givenStartedGameAndSeatToken_whenGetGame_thenReturnsGameView() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        String t0 = sessionService.tokenForSeat(id, new PlayerId(0));

        ResponseEntity<?> response = controller.getGame(id.uuid().toString(), t0);

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), instanceOf(BullshitDto.class));
        assertThat(((BullshitDto) response.getBody()).started(), is(true));
    }

    @Test
    void givenLobbyAndHostToken_whenGetGame_thenReturnsLobbyView() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");

        ResponseEntity<?> response = controller.getGame(room.gameId(), room.hostToken());

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), instanceOf(LobbyView.class));
        assertThat(((LobbyView) response.getBody()).started(), is(false));
    }

    @Test
    void givenNoToken_whenGetGame_thenForbidden() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");

        ResponseEntity<?> response = controller.getGame(room.gameId(), null);

        assertThat(response.getStatusCode().value(), is(403));
    }

    @Test
    void givenUnknownGame_whenGetGame_thenNotFound() {
        ResponseEntity<?> response = controller.getGame(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value(), is(404));
    }

    @Test
    void givenOpenRoom_whenJoin_thenReturnsSeat1AndBroadcastsLobbyToClaimedSeats() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(room.gameId(), new JoinGamePayload("Bob"));

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody().playerId(), is(1));
        assertThat(response.getBody().token(), is(notNullValue()));
        assertThat(messaging.seats.size(), is(2));
        assertThat(messaging.eventTypes, everyItem(is("JOIN")));
    }

    @Test
    void givenStartedGame_whenJoin_thenConflict() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "Bob");
        sessionService.startGame(id, room.hostToken());

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(room.gameId(), new JoinGamePayload("Late"));

        assertThat(response.getStatusCode().value(), is(409));
    }

    @Test
    void givenFullRoom_whenJoin_thenConflict() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        for (int i = 1; i < 6; i++) {
            sessionService.joinRoom(id, "P" + i);
        }

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(room.gameId(), new JoinGamePayload("Late"));

        assertThat(response.getStatusCode().value(), is(409));
    }

    @Test
    void givenUnknownGame_whenJoin_thenNotFound() {
        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(UUID.randomUUID().toString(), null);

        assertThat(response.getStatusCode().value(), is(404));
    }

    @Test
    void playAgain_onFinishedRoom_reopensAndReturnsHostSeat() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        GameId id = new GameId(room.gameId());
        sessionService.joinRoom(id, "Bob");
        sessionService.startGame(id, room.hostToken());

        ResponseEntity<JoinResponseDto> response =
                controller.playAgain(room.gameId(), new JoinGamePayload("Alice"));

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().playerId(), is(0));
    }
}
