package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end test of the Bataille Corse STOMP protocol over a real WebSocket: the game is created over
 * /app/create (answered on the lobby topic /topic/game, with one token per seat in SOLO mode), then game
 * events are received on /topic/game/{id} after sending token-authenticated actions to /app/send and /app/slap.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatailleCorseWebSocketControllerIT {

    private static final int TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    private StompSession stompSession;

    @BeforeEach
    void setUp() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Raw JSON strings in both directions: let the string converter accept application/json too.
        stompClient.setMessageConverter(new StringMessageConverter() {
            {
                addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
            }
        });
        stompSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenNoGame_whenCreateGame_thenResponseIsSuccessAndGameStateIsConsistent() throws Exception {
        Map<String, Object> response = createGame();

        assertThat(response.get("success"), is(true));
        assertThat(response.get("eventType"), is("CREATE"));
        assertThat(response.get("message"), is("Game created"));

        Map<String, Object> state = (Map<String, Object>) response.get("state");
        assertThat(state, notNullValue());

        // Two players, each holding half the deck (52 / 2 = 26)
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        assertThat(players, hasSize(2));
        for (Map<String, Object> player : players) {
            assertThat(player.get("nbCards"), is(26));
        }

        // Pile starts empty and cannot be grabbed
        Map<String, Object> pile = (Map<String, Object>) state.get("pile");
        assertThat((List<?>) pile.get("cards"), empty());
        assertThat(pile.get("grabbable"), is(false));

        // First player is current and can only SEND (pile is empty so SLAP/GRAB unavailable)
        Map<String, Object> currentPlayer = (Map<String, Object>) state.get("currentPlayer");
        assertThat((List<String>) currentPlayer.get("availableActions"), contains("SEND"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenSoloGame_whenCreateGame_thenOneTokenPerSeatIsReturned() throws Exception {
        Map<String, Object> eventData = (Map<String, Object>) createGame().get("eventData");

        Map<String, String> tokens = (Map<String, String>) eventData.get("tokens");
        assertThat(tokens.keySet(), containsInAnyOrder("0", "1"));
        assertThat(tokens.get("0"), not(equalTo(tokens.get("1"))));
        // Tokens are UUIDs
        UUID.fromString(tokens.get("0"));
        UUID.fromString(tokens.get("1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenGameAndSeatToken_whenSend_thenGameTopicReceivesSendEventAndTurnPasses() throws Exception {
        Map<String, Object> created = createGame();
        String gameId = gameIdOf(created);
        String token = tokenOf(created, 0);
        CompletableFuture<Map<String, Object>> events = subscribeToGame(gameId);

        sendJson("/app/send", payload(gameId, token));

        Map<String, Object> event = events.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event.get("success"), is(true));
        assertThat(event.get("eventType"), is("SEND"));

        Map<String, Object> state = (Map<String, Object>) event.get("state");
        Map<String, Object> pile = (Map<String, Object>) state.get("pile");
        assertThat((List<?>) pile.get("cards"), hasSize(1));
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        assertThat(players.get(0).get("nbCards"), is(25));
        assertThat(players.get(1).get("nbCards"), is(26));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenGameAndOtherSeatToken_whenSendOutOfTurn_thenGameTopicReceivesErrorAndStateIsUnchanged() throws Exception {
        Map<String, Object> created = createGame();
        String gameId = gameIdOf(created);
        String tokenOfSecondPlayer = tokenOf(created, 1);
        CompletableFuture<Map<String, Object>> events = subscribeToGame(gameId);

        sendJson("/app/send", payload(gameId, tokenOfSecondPlayer));

        Map<String, Object> event = events.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event.get("success"), is(false));
        assertThat(event.get("eventType"), is("SEND"));
        Map<String, Object> state = (Map<String, Object>) event.get("state");
        assertThat((List<?>) ((Map<String, Object>) state.get("pile")).get("cards"), empty());
    }

    @Test
    void givenGame_whenSendWithUnknownToken_thenGameTopicReceivesErrorResponse() throws Exception {
        String gameId = gameIdOf(createGame());
        CompletableFuture<Map<String, Object>> events = subscribeToGame(gameId);

        sendJson("/app/send", payload(gameId, UUID.randomUUID().toString()));

        Map<String, Object> event = events.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event.get("success"), is(false));
        assertThat(event.get("eventType"), is("SEND"));
    }

    @Test
    void givenEmptyPile_whenSlap_thenGameTopicReceivesSlapEvent() throws Exception {
        Map<String, Object> created = createGame();
        String gameId = gameIdOf(created);
        CompletableFuture<Map<String, Object>> events = subscribeToGame(gameId);

        sendJson("/app/slap", payload(gameId, tokenOf(created, 0)));

        Map<String, Object> event = events.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event.get("eventType"), is("SLAP"));
        assertThat(event.get("state"), notNullValue());
    }

    // ---- helpers ----

    private Map<String, Object> createGame() throws Exception {
        CompletableFuture<Map<String, Object>> created = subscribe("/topic/game");
        sendJson("/app/create", "{\"mode\":\"SOLO\"}");
        return created.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private CompletableFuture<Map<String, Object>> subscribeToGame(String gameId) throws Exception {
        return subscribe("/topic/game/" + gameId);
    }

    /** Subscribes and returns a future of the first frame, only once the broker has registered the subscription. */
    private CompletableFuture<Map<String, Object>> subscribe(String destination) throws Exception {
        CompletableFuture<Map<String, Object>> firstFrame = new CompletableFuture<>();

        stompSession.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    firstFrame.complete(objectMapper.readValue((String) payload, Map.class));
                } catch (Exception e) {
                    firstFrame.completeExceptionally(e);
                }
            }
        });

        awaitBrokerSubscription(destination);
        return firstFrame;
    }

    /**
     * The simple broker sends no RECEIPT frames, and SUBSCRIBE / SEND are handled on separate threads, so an
     * action sent right after a SUBSCRIBE could be broadcast before the subscription exists. Poll the broker's
     * own registry (bounded by a deadline, no fixed sleep) until the subscription is registered.
     */
    private void awaitBrokerSubscription(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (broker.getSubscriptionRegistry().findSubscriptions(probe).isEmpty()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Subscription to " + destination + " was not registered by the broker");
            }
            Thread.onSpinWait();
        }
    }

    /** The server decodes payloads as JSON, so the frame must declare its content type. */
    private void sendJson(String destination, String json) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        stompSession.send(headers, json);
    }

    private String payload(String gameId, String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of("gameId", gameId, "token", token));
    }

    @SuppressWarnings("unchecked")
    private static String gameIdOf(Map<String, Object> created) {
        Map<String, Object> eventData = (Map<String, Object>) created.get("eventData");
        Map<String, Object> game = (Map<String, Object>) eventData.get("game");
        return (String) game.get("id");
    }

    @SuppressWarnings("unchecked")
    private static String tokenOf(Map<String, Object> created, int seat) {
        Map<String, Object> eventData = (Map<String, Object>) created.get("eventData");
        return ((Map<String, String>) eventData.get("tokens")).get(String.valueOf(seat));
    }
}
