package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class BullshitRestControllerTest {

    private SessionService sessionService;
    private BullshitRestController controller;
    private RecordingMessaging messaging;

    static final class RecordingMessaging extends org.kevinkib.cardgames.presentation.GameMessagingService {
        final java.util.List<Integer> seats = new java.util.ArrayList<>();
        final java.util.List<String> eventTypes = new java.util.ArrayList<>();
        RecordingMessaging() { super(null); }
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
        controller = new BullshitRestController(sessionService, new BullshitStateBroadcaster(messaging));
    }

    @Test
    void givenValidToken_whenGetGame_thenReturnsOwnView() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        SessionToken t0 = sessionService.loadTokenByPlayerId(id, new PlayerId(0));

        ResponseEntity<BullshitDto> response = controller.getGame(id.uuid().toString(), t0.uuid().toString());

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody().myHand().isEmpty(), is(false));
    }

    @Test
    void givenNoToken_whenGetGame_thenForbidden() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);

        ResponseEntity<BullshitDto> response = controller.getGame(game.getId().uuid().toString(), null);

        assertThat(response.getStatusCode().value(), is(403));
    }

    @Test
    void givenUnknownGame_whenGetGame_thenNotFound() {
        ResponseEntity<BullshitDto> response = controller.getGame(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value(), is(404));
    }

    @Test
    void givenOpenSeat_whenJoin_thenReturnsSeat1TokenAndBroadcastsJoinToAllSeats() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.MULTIPLAYER);
        String id = game.getId().uuid().toString();

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(id, new JoinGamePayload("Bob"));

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody().playerId(), is(1));
        assertThat(response.getBody().token(), is(notNullValue()));
        assertThat(messaging.seats.size(), is(2));
        assertThat(messaging.eventTypes, everyItem(is("JOIN")));
    }

    @Test
    void givenSeatAlreadyTaken_whenJoin_thenConflict() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.MULTIPLAYER);
        String id = game.getId().uuid().toString();
        controller.joinGame(id, new JoinGamePayload("Bob"));

        ResponseEntity<JoinResponseDto> second = controller.joinGame(id, new JoinGamePayload("Carol"));

        assertThat(second.getStatusCode().value(), is(409));
    }

    @Test
    void givenUnknownGame_whenJoin_thenNotFound() {
        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(java.util.UUID.randomUUID().toString(), null);

        assertThat(response.getStatusCode().value(), is(404));
    }
}
