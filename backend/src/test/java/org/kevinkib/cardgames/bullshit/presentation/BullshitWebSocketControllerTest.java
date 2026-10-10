package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotDecision;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitBotPayload;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.bullshit.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import java.time.Duration;
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
    private ManualBotScheduler botScheduler;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        messaging = new RecordingMessaging();
        BullshitStateBroadcaster broadcaster = new BullshitStateBroadcaster(messaging, sessionService::botSeats);
        BullshitGameActions actions = new BullshitGameActions(sessionService, broadcaster);
        botScheduler = new ManualBotScheduler();
        broadcaster.addListener(new BullshitBotCoordinator(
                sessionService::botSeats, actions, botScheduler, new FixedThinkingDelay(Duration.ZERO),
                game -> (observation, memory) -> new BotDecision.Pass()));
        controller = new BullshitWebSocketController(sessionService, broadcaster, messaging, actions,
                new LobbyBroadcaster(messaging, sessionService));
    }

    private BullshitCreateEventData createRoom() {
        Response create = controller.createGame(new BullshitCreatePayload("Alice", null, null));
        return (BullshitCreateEventData) create.getEventData();
    }

    @Test
    void givenHost_whenAddBot_thenEveryHumanSeatReceivesALobbyWithTheBot() {
        BullshitCreateEventData room = createRoom();
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.tokens().get(0)));

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        Response response = messaging.payloads.get(0);
        assertThat(response.isSuccess(), is(true));
        assertThat(response.getEventType(), is("JOIN"));
        LobbyView lobby = (LobbyView) response.getState();
        assertThat(lobby.players().get(2).bot(), is(true));
        assertThat(lobby.players().get(2).joined(), is(true));
        assertThat(lobby.players().get(2).name(), is("Bot 1"));
    }

    @Test
    void givenNonHost_whenAddBot_thenErrorToTheActingSeatOnly() {
        BullshitCreateEventData room = createRoom();
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), bobToken));

        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(sessionService.botSeats(new GameId(room.gameId())).isEmpty(), is(true));
    }

    @Test
    void givenStartedGame_whenAddBot_thenErrorToTheHost() {
        BullshitCreateEventData room = createRoom();
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        controller.start(new GameActionPayload(room.gameId(), room.tokens().get(0)));
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.tokens().get(0)));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenUnknownToken_whenAddBot_thenIgnored() {
        BullshitCreateEventData room = createRoom();
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), java.util.UUID.randomUUID().toString()));

        assertThat(messaging.seats.isEmpty(), is(true));
    }

    @Test
    void givenBot_whenHostRemovesIt_thenLobbyRefreshWithoutTheBot() {
        BullshitCreateEventData room = createRoom();
        String hostToken = room.tokens().get(0);
        controller.addBot(new GameActionPayload(room.gameId(), hostToken));
        messaging.clear();

        controller.removeBot(new BullshitBotPayload(room.gameId(), hostToken, 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        LobbyView lobby = (LobbyView) messaging.payloads.get(0).getState();
        assertThat(lobby.players().get(1).joined(), is(false));
        assertThat(lobby.players().get(1).bot(), is(false));
    }

    @Test
    void givenHumanAfterTheBot_whenHostRemovesIt_thenRefusedWithAnError() {
        BullshitCreateEventData room = createRoom();
        String hostToken = room.tokens().get(0);
        controller.addBot(new GameActionPayload(room.gameId(), hostToken));
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        messaging.clear();

        controller.removeBot(new BullshitBotPayload(room.gameId(), hostToken, 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(sessionService.botSeats(new GameId(room.gameId())).size(), is(1));
    }

    @Test
    void givenNonHost_whenRemoveBot_thenErrorToTheActingSeat() {
        BullshitCreateEventData room = createRoom();
        controller.addBot(new GameActionPayload(room.gameId(), room.tokens().get(0)));
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();

        controller.removeBot(new BullshitBotPayload(room.gameId(), bobToken, 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(2))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenHostAndOneBot_whenStartThenHostPlays_thenTheBotGetsATaskAndNoMessage() {
        BullshitCreateEventData room = createRoom();
        String hostToken = room.tokens().get(0);
        controller.addBot(new GameActionPayload(room.gameId(), hostToken));
        GameId id = new GameId(room.gameId());

        controller.start(new GameActionPayload(room.gameId(), hostToken));

        assertThat(botScheduler.pending().size(), is(0)); // the host is first to play
        Bullshit game = sessionService.getGame(id, Bullshit.class);
        messaging.clear();
        Card card = game.getPlayers().get(0).getCards().get(0);

        controller.discard(new BullshitDiscardPayload(room.gameId(), hostToken, List.of(CardDto.from(card))));

        assertThat(botScheduler.pending().size(), is(1)); // now the bot has to answer
        assertThat(messaging.seats, is(List.of(new PlayerId(0)))); // only the human is messaged
    }

    @Test
    void givenCreate_whenCreate_thenRoomAckWithHostTokenNoState() {
        Response response = controller.createGame(new BullshitCreatePayload("Alice", null, null));

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
        Response create = controller.createGame(new BullshitCreatePayload("Alice", null, null));
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
        Response create = controller.createGame(new BullshitCreatePayload("Alice", null, null));
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
