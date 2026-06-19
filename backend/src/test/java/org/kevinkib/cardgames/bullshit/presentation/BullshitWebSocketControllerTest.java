package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitCreatePayload;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitDiscardPayload;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitCreateEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.CallBullshitEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class BullshitWebSocketControllerTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Response> payloads = new ArrayList<>();

        RecordingMessaging() {
            super(null, null);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }

        void clear() {
            seats.clear();
            payloads.clear();
        }
    }

    private SessionService sessionService;
    private RecordingMessaging messaging;
    private BullshitWebSocketController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        messaging = new RecordingMessaging();
        controller = new BullshitWebSocketController(
                sessionService, new BullshitStateBroadcaster(messaging), messaging);
    }

    @Test
    void givenCreate_whenCreate_thenRoomAckWithHostTokenNoState() {
        Response response = controller.createGame(new BullshitCreatePayload(null, null, "Alice"));

        assertThat(response.isSuccess(), is(true));
        assertThat(response.getEventType(), is("CREATE"));
        assertThat(response.getState(), is(nullValue()));
        BullshitCreateEventData data = (BullshitCreateEventData) response.getEventData();
        assertThat(data.gameType(), is("bullshit"));
        assertThat(data.tokens().size(), is(1));
        assertThat(data.tokens().containsKey(0), is(true));
    }

    @Test
    void givenHostStartsWithEnoughPlayers_whenStart_thenBroadcastsGameToAllSeats() {
        Response create = controller.createGame(new BullshitCreatePayload(null, null, "Alice"));
        BullshitCreateEventData data = (BullshitCreateEventData) create.getEventData();
        GameId id = new GameId(data.gameId());
        sessionService.joinRoom(id, "Bob");
        String hostToken = data.tokens().get(0);

        controller.start(new GameActionPayload(data.gameId(), hostToken));

        assertThat(messaging.seats.size(), is(2));
        assertThat(messaging.payloads.get(0).getEventType(), is("START"));
        assertThat(messaging.payloads.get(0).isSuccess(), is(true));
        assertThat(messaging.payloads.get(0).getState(), instanceOf(BullshitDto.class));
    }

    @Test
    void givenNonHostStart_whenStart_thenErrorToActingSeatOnly() {
        Response create = controller.createGame(new BullshitCreatePayload(null, null, "Alice"));
        BullshitCreateEventData data = (BullshitCreateEventData) create.getEventData();
        GameId id = new GameId(data.gameId());
        var bob = sessionService.joinRoom(id, "Bob");

        controller.start(new GameActionPayload(data.gameId(), bob.token()));

        assertThat(messaging.seats.size(), is(1));
        assertThat(messaging.seats.get(0), is(new PlayerId(1)));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(messaging.payloads.get(0).getEventType(), is("START"));
    }

    @Test
    void givenValidDiscard_whenDiscard_thenBroadcastsDiscardToAllSeats() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        String t0 = sessionService.tokenForSeat(id, new PlayerId(0));
        Card card = game.getPlayers().get(0).getCards().get(0);

        controller.discard(new BullshitDiscardPayload(
                id.uuid().toString(), t0, List.of(CardDto.from(card))));

        assertThat(messaging.seats.size(), is(2));
        Response r = messaging.payloads.get(0);
        assertThat(r.getEventType(), is("DISCARD"));
        assertThat(r.getEventData(), instanceOf(DiscardEventData.class));
        assertThat(((DiscardEventData) r.getEventData()).count(), is(1));
    }

    @Test
    void givenNotYourTurn_whenDiscard_thenErrorToActingSeatOnly() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        String t1 = sessionService.tokenForSeat(id, new PlayerId(1));
        Card card = game.getPlayers().get(1).getCards().get(0);

        controller.discard(new BullshitDiscardPayload(
                id.uuid().toString(), t1, List.of(CardDto.from(card))));

        assertThat(messaging.seats.size(), is(1));
        assertThat(messaging.seats.get(0), is(new PlayerId(1)));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenClaimOnTable_whenCallBullshit_thenBroadcastsRevealWithCards() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        String t0 = sessionService.tokenForSeat(id, new PlayerId(0));
        Card c0 = game.getPlayers().get(0).getCards().get(0);
        controller.discard(new BullshitDiscardPayload(
                id.uuid().toString(), t0, List.of(CardDto.from(c0))));
        messaging.clear();

        String t1 = sessionService.tokenForSeat(id, new PlayerId(1));
        controller.callBullshit(new GameActionPayload(id.uuid().toString(), t1));

        Response r = messaging.payloads.get(0);
        assertThat(r.getEventType(), is("CALL_BULLSHIT"));
        CallBullshitEventData data = (CallBullshitEventData) r.getEventData();
        assertThat(data.revealedCards().size(), is(1));
    }

}
