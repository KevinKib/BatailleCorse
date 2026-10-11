package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Lobby commands over a real WebSocket: rename, bots and seat options reach every seat live. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyControllerIT {

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

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
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

    private static final class Frames {
        final BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();

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
    }

    private Frames subscribeSeat(StompSession session, String gameId, String token) {
        String destination = "/topic/game/" + gameId + "/seat/" + token;
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
                frames.queue.add(objectMapper.readValue((String) payload, Map.class));
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
        headers.setDestination(destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, objectMapper.writeValueAsString(body));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> players(Map<String, Object> frame) {
        Map<String, Object> state = (Map<String, Object>) frame.get("state");
        return state == null ? List.of() : (List<Map<String, Object>>) state.get("players");
    }

    @Test
    void guestRenames_hostSeesTheNewNameLive() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = connect();
        StompSession guest = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());
        subscribeSeat(guest, room.gameId(), guestToken);

        send(guest, "/app/lobby/rename", Map.of("gameId", room.gameId(), "token", guestToken, "name", "Robert"));

        hostFrames.await(f -> players(f).size() > 1 && "Robert".equals(players(f).get(1).get("name")));
    }

    @Test
    void hostAddsAndRemovesABot_onABullshitLobby() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        StompSession host = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());

        send(host, "/app/lobby/addBot", Map.of("gameId", room.gameId(), "token", room.hostToken()));
        hostFrames.await(f -> players(f).size() > 1 && Boolean.TRUE.equals(players(f).get(1).get("bot")));

        send(host, "/app/lobby/removeBot", Map.of("gameId", room.gameId(), "token", room.hostToken(), "seat", 1));
        hostFrames.await(f -> players(f).size() > 1 && Boolean.FALSE.equals(players(f).get(1).get("joined")));
    }

    @Test
    void hostAddsABot_onABatailleCorseLobby() throws Exception {
        RoomCreated room = sessionService.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");
        StompSession host = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());

        send(host, "/app/lobby/addBot", Map.of("gameId", room.gameId(), "token", room.hostToken()));

        hostFrames.await(f -> players(f).size() > 1 && Boolean.TRUE.equals(players(f).get(1).get("bot")));
    }

    @Test
    void invalidSeatOption_isRefusedToTheHost() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        sessionService.addBot(new GameId(room.gameId()), room.hostToken());
        StompSession host = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());

        send(host, "/app/lobby/setSeatOption",
                Map.of("gameId", room.gameId(), "token", room.hostToken(), "seat", 1, "key", "tier", "value", "3"));

        hostFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
    }
}
