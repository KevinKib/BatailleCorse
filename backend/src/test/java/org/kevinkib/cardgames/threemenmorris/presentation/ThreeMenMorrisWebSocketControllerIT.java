package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Three Men's Morris over a real WebSocket with two clients: lobby creation, join, start, a game up
 * to the victory, a rejected illegal move, a rejected move out of turn, and rehydration through REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThreeMenMorrisWebSocketControllerIT {

    private static final int TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionService sessionService;

    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sessions.clear();
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    /** A started two-player table: both clients subscribed to their private seat topic. */
    private final class Table {
        final String gameId;
        final String[] tokens = new String[2];
        final StompSession[] stomp = new StompSession[2];
        final Frames[] frames = new Frames[2];

        @SuppressWarnings("unchecked")
        Table() throws Exception {
            HttpResponse<String> created = post("/api/lobby", "{\"gameType\":\"three-men-morris\",\"name\":\"Alice\"}");
            assertThat(created.statusCode(), is(200));
            Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
            gameId = (String) room.get("gameId");
            tokens[0] = (String) room.get("token");
            assertThat(sessionService.gameType(new GameId(gameId)), is("three-men-morris"));

            HttpResponse<String> joined = post("/api/three-men-morris/game/" + gameId + "/join", "{\"name\":\"Bob\"}");
            assertThat(joined.statusCode(), is(200));
            tokens[1] = (String) objectMapper.readValue(joined.body(), Map.class).get("token");

            for (int seat = 0; seat < 2; seat++) {
                stomp[seat] = connect();
                frames[seat] = subscribe(stomp[seat], "/topic/game/" + gameId + "/seat/" + tokens[seat], tokens[seat]);
            }
            send(0, "/three-men-morris/start", body(tokens[0]));
            for (int seat = 0; seat < 2; seat++) {
                Map<String, Object> start = frames[seat].await(f -> "START".equals(f.get("eventType")));
                assertThat(state(start).get("phase"), is("PLACEMENT"));
            }
        }

        void send(int seat, String destination, Map<String, Object> body) {
            ThreeMenMorrisWebSocketControllerIT.this.send(stomp[seat], destination, body);
        }

        Map<String, Object> body(String token, Object... more) {
            Map<String, Object> map = new HashMap<>();
            map.put("gameId", gameId);
            map.put("token", token);
            for (int i = 0; i < more.length; i += 2) {
                map.put((String) more[i], more[i + 1]);
            }
            return map;
        }

        /** Places a piece and waits until both seats have seen the event. */
        void place(int seat, int point, String expectedEvent) throws Exception {
            send(seat, "/three-men-morris/place", body(tokens[seat], "point", point));
            for (int s = 0; s < 2; s++) {
                frames[s].await(f -> expectedEvent.equals(f.get("eventType"))
                        && Boolean.TRUE.equals(f.get("success")));
            }
        }

        /** Moves a piece and waits until both seats have seen the event. */
        void move(int seat, int from, int to, String expectedEvent) throws Exception {
            send(seat, "/three-men-morris/move", body(tokens[seat], "from", from, "to", to));
            for (int s = 0; s < 2; s++) {
                frames[s].await(f -> expectedEvent.equals(f.get("eventType"))
                        && Boolean.TRUE.equals(f.get("success")));
            }
        }

        Map<String, Object> rehydrate(int seat) throws Exception {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/three-men-morris/game/"
                            + gameId + "?token=" + tokens[seat])).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode(), is(200));
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            return parsed;
        }
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private StompSession connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter() {
            {
                addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
            }
        });
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    /** All the frames a subscription receives, in order. */
    private static final class Frames {
        final BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();

        /** Every frame received, whatever {@link #await} consumed. */
        final List<Map<String, Object>> all = new java.util.concurrent.CopyOnWriteArrayList<>();

        Map<String, Object> await(Predicate<Map<String, Object>> predicate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                Map<String, Object> frame = queue.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null && predicate.test(frame)) {
                    return frame;
                }
            }
            throw new AssertionError("No matching frame received");
        }

        List<Map<String, Object>> drain() {
            List<Map<String, Object>> all = new ArrayList<>();
            queue.drainTo(all);
            return all;
        }
    }

    private Frames subscribe(StompSession session, String destination, String token) {
        Frames frames = new Frames();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.add("token", token);
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> frame = objectMapper.readValue((String) payload, Map.class);
                frames.all.add(frame);
                frames.queue.add(frame);
            }
        });
        awaitBrokerSubscription(destination);
        return frames;
    }

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

    private void send(StompSession session, String destination, Map<String, Object> body) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app" + destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, objectMapper.writeValueAsString(body));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    // ------------------------------------------------------------------ tests

    @Test
    @SuppressWarnings("unchecked")
    void aWholeGameUpToTheVictory_isBroadcastToBothSeats() throws Exception {
        Table table = new Table();

        table.place(0, 0, "PLACE");
        table.place(1, 3, "PLACE");
        table.place(0, 1, "PLACE");
        table.place(1, 4, "PLACE");
        table.send(0, "/three-men-morris/place", table.body(table.tokens[0], "point", 2));

        for (int seat = 0; seat < 2; seat++) {
            Map<String, Object> over = table.frames[seat].await(f -> "GAME_OVER".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(over).get("outcome");
            assertThat(outcome.get("status"), is("FINISHED"));
            assertThat(outcome.get("winner"), is(0));
            assertThat(outcome.get("reason"), is("LINE"));
            assertThat(state(over).get("winningLine"), is(List.of(0, 1, 2)));
        }
        assertThat(table.rehydrate(1).get("phase"), is("FINISHED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMovementGameEndsOnTheDiagonal_andIllegalOrOutOfTurnMovesAreRefusedToTheSenderOnly() throws Exception {
        Table table = new Table();
        // placement without a line: white 0, 2, 7 and black 1, 5, 8
        table.place(0, 0, "PLACE");
        table.place(1, 1, "PLACE");
        table.place(0, 2, "PLACE");
        table.place(1, 5, "PLACE");
        table.place(0, 7, "PLACE");
        table.place(1, 8, "PLACE");
        assertThat(table.rehydrate(0).get("phase"), is("MOVEMENT"));
        int version = (Integer) table.rehydrate(0).get("version");

        // black plays out of turn, white tries a jump: both refused, each to its sender only
        table.send(1, "/three-men-morris/move", table.body(table.tokens[1], "from", 1, "to", 4));
        table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")) && "MOVE".equals(f.get("eventType")));
        table.send(0, "/three-men-morris/move", table.body(table.tokens[0], "from", 0, "to", 6));
        table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")) && "MOVE".equals(f.get("eventType")));
        assertThat(table.rehydrate(0).get("version"), is(version));

        table.move(0, 7, 4, "MOVE");
        table.move(1, 8, 7, "MOVE");
        table.move(0, 0, 3, "MOVE");
        table.move(1, 1, 0, "MOVE");
        table.move(0, 3, 6, "GAME_OVER");

        Map<String, Object> last = table.rehydrate(1);
        Map<String, Object> outcome = (Map<String, Object>) last.get("outcome");
        assertThat(outcome.get("winner"), is(0));
        assertThat(outcome.get("reason"), is("LINE"));
        assertThat(last.get("winningLine"), is(List.of(2, 4, 6)));
        assertThat(table.frames[1].all.stream().filter(f -> Boolean.FALSE.equals(f.get("success"))).count(), is(1L));
        assertThat(table.frames[0].all.stream().filter(f -> Boolean.FALSE.equals(f.get("success"))).count(), is(1L));
    }

    @Test
    void aPlacementOutOfTurnOrOnAnOccupiedPointIsRefusedAndNothingChanges() throws Exception {
        Table table = new Table();
        table.place(0, 4, "PLACE");

        table.send(0, "/three-men-morris/place", table.body(table.tokens[0], "point", 0));
        table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")));
        table.send(1, "/three-men-morris/place", table.body(table.tokens[1], "point", 4));
        table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")));

        Map<String, Object> rest = table.rehydrate(0);
        assertThat(rest.get("version"), is(1));
        assertThat(rest.get("currentSeat"), is(1));
        assertThat(table.frames[0].all.stream().filter(f -> Boolean.FALSE.equals(f.get("success"))).count(), is(1L));
        assertThat(table.frames[1].all.stream().filter(f -> Boolean.FALSE.equals(f.get("success"))).count(), is(1L));
    }

    @Test
    void aSeatCannotPlayWithTheOtherSeatsTokenNorWithoutOne() throws Exception {
        Table table = new Table();

        table.send(1, "/three-men-morris/place", table.body("forged", "point", 4));
        table.send(1, "/three-men-morris/place", table.body(null, "point", 4));
        table.place(0, 4, "PLACE");

        assertThat(table.rehydrate(0).get("version"), is(1));
        assertThat(table.frames[1].all.stream().filter(f -> Boolean.FALSE.equals(f.get("success"))).count(), is(0L));
    }
}
