package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.bot.BotDecision;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import java.time.Duration;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitDiscardPayload;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.CallBullshitEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class BullshitWebSocketControllerTest {

    
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
                sessionService::botSeats, actions, botScheduler, new FixedThinkingDelay(Duration.ZERO), () -> Duration.ZERO,
                game -> (observation, memory) -> new BotDecision.Pass()));
        controller = new BullshitWebSocketController(sessionService, broadcaster,
                new org.kevinkib.cardgames.presentation.command.SeatCommands(sessionService, messaging), actions);
    }

    private RoomCreated createRoom() {
        return sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
    }

    @Test
    void givenHostAndOneBot_whenStartThenHostPlays_thenTheBotGetsATaskAndNoMessage() {
        RoomCreated room = createRoom();
        String hostToken = room.hostToken();
        sessionService.addBot(new GameId(room.gameId()), hostToken);
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
    void givenController_thenNoHandlerCreatesALobbyOverTheSharedTopic() {
        // Lobbies are created over POST /api/lobby: a /bullshit/create answer on /topic/game leaked host tokens.
        for (java.lang.reflect.Method method : BullshitWebSocketController.class.getDeclaredMethods()) {
            org.springframework.messaging.handler.annotation.MessageMapping mapping =
                    method.getAnnotation(org.springframework.messaging.handler.annotation.MessageMapping.class);
            if (mapping != null) {
                assertThat(java.util.Arrays.asList(mapping.value()).contains("/bullshit/create"), is(false));
            }
            assertThat(method.getAnnotation(org.springframework.messaging.handler.annotation.SendTo.class), is(nullValue()));
        }
    }

    @Test
    void givenHostStartsWithEnoughPlayers_whenStart_thenBroadcastsGameToAllSeats() {
        RoomCreated data = createRoom();
        GameId id = new GameId(data.gameId());
        sessionService.joinRoom(id, "Bob");
        String hostToken = data.hostToken();

        controller.start(new GameActionPayload(data.gameId(), hostToken));

        assertThat(messaging.seats.size(), is(2));
        assertThat(messaging.payloads.get(0).getEventType(), is("START"));
        assertThat(messaging.payloads.get(0).isSuccess(), is(true));
        assertThat(messaging.payloads.get(0).getState(), instanceOf(BullshitDto.class));
    }

    @Test
    void givenNonHostStart_whenStart_thenErrorToActingSeatOnly() {
        RoomCreated data = createRoom();
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

    // ---- contract of the error reply (owner decision: the sender's state is attached when a game runs)

    @Test
    void aRefusedDiscard_repliesToTheSenderAloneWithTheMessageAndItsState() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        String t1 = sessionService.tokenForSeat(id, new PlayerId(1));
        Card card = game.getPlayers().get(1).getCards().get(0);

        controller.discard(new BullshitDiscardPayload(id.uuid().toString(), t1, List.of(CardDto.from(card))));

        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        Response error = messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("DISCARD"));
        assertThat(error.getMessage(), is("Not the turn of player " + new PlayerId(1)));
        assertThat(error.getState(), instanceOf(BullshitDto.class));
    }

    @Test
    void aRefusedCallBullshit_repliesToTheSenderAloneWithTheMessageAndItsState() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        String t1 = sessionService.tokenForSeat(id, new PlayerId(1));

        controller.callBullshit(new GameActionPayload(id.uuid().toString(), t1));

        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        Response error = messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("CALL_BULLSHIT"));
        assertThat(error.getMessage(), is(notNullValue()));
        assertThat(error.getState(), instanceOf(BullshitDto.class));
    }

    @Test
    void aRefusedStartOfALobby_repliesToTheSenderAloneWithoutState() {
        RoomCreated room = createRoom();

        controller.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        Response error = messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("START"));
        assertThat(error.getMessage(), is(notNullValue()));
        assertThat(error.getState(), is(nullValue()));
    }

    @Test
    void aRefusedStartOfARunningGame_repliesToTheSenderAloneWithItsState() {
        RoomCreated room = createRoom();
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        controller.start(new GameActionPayload(room.gameId(), room.hostToken()));
        messaging.clear();

        controller.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        Response error = messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("START"));
        assertThat(error.getState(), instanceOf(BullshitDto.class));
    }

    @Test
    void anUnknownTokenIsIgnored() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        String id = game.getId().uuid().toString();

        controller.discard(new BullshitDiscardPayload(id, "not-a-token", List.of()));
        controller.callBullshit(new GameActionPayload(id, "not-a-token"));
        controller.start(new GameActionPayload(id, "not-a-token"));

        assertThat(messaging.payloads.isEmpty(), is(true));
        assertThat(messaging.toGame.isEmpty(), is(true));
    }

    @Test
    void aMalformedOrUnknownGameId_isIgnoredWithoutAnyException() {
        for (String rawId : new String[]{"not-a-uuid", "", java.util.UUID.randomUUID().toString()}) {
            controller.discard(new BullshitDiscardPayload(rawId, "token", List.of()));
            controller.callBullshit(new GameActionPayload(rawId, "token"));
            controller.start(new GameActionPayload(rawId, "token"));
        }

        assertThat(messaging.payloads.isEmpty(), is(true));
        assertThat(messaging.toGame.isEmpty(), is(true));
    }
}
