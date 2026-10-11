package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

/** Leaving a lobby over a real WebSocket: the seats shift and every remaining client follows on its token channel. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyLeaveIT {

    private static final int TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry connectionRegistry;

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @Test
    void guestLeaves_hostSeesTheFreeSeat() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = connect();
        StompSession guest = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());
        subscribeSeat(guest, room.gameId(), guestToken);

        send(guest, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", guestToken));

        hostFrames.await(f -> players(f).size() > 1 && Boolean.FALSE.equals(players(f).get(1).get("joined")));
    }

    @Test
    void hostLeaves_theNextClientBecomesHostOnTheSameSubscription() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        String carolToken = sessionService.joinRoom(new GameId(room.gameId()), "Carol").token();
        StompSession host = connect();
        StompSession bob = connect();
        StompSession carol = connect();
        subscribeSeat(host, room.gameId(), room.hostToken());
        Frames bobFrames = subscribeSeat(bob, room.gameId(), bobToken);
        Frames carolFrames = subscribeSeat(carol, room.gameId(), carolToken);

        send(host, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", room.hostToken()));

        Map<String, Object> bobView = state(bobFrames.await(f -> state(f) != null
                && Integer.valueOf(0).equals(state(f).get("mySeat"))));
        assertThat(bobView.get("canStart"), is(true));
        assertThat(bobView.get("hostSeat"), is(0));
        Map<String, Object> carolView = state(carolFrames.await(f -> state(f) != null
                && Integer.valueOf(1).equals(state(f).get("mySeat"))));
        assertThat(carolView.get("canStart"), is(false));
    }

    @Test
    void leaversOldSubscription_receivesNothingAfterANewcomerTakesTheSeat() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        String bobToken = sessionService.joinRoom(id, "Bob").token();
        StompSession host = connect();
        StompSession bob = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());
        Frames bobFrames = subscribeSeat(bob, room.gameId(), bobToken);
        send(bob, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", bobToken));
        hostFrames.await(f -> players(f).size() > 1 && Boolean.FALSE.equals(players(f).get(1).get("joined")));

        String carolToken = sessionService.joinRoom(id, "Carol").token();
        StompSession carol = connect();
        Frames carolFrames = subscribeSeat(carol, room.gameId(), carolToken);
        send(host, "/app/lobby/rename", Map.of("gameId", room.gameId(), "token", room.hostToken(), "name", "Alicia"));
        carolFrames.await(f -> players(f).size() > 1 && "Alicia".equals(players(f).get(0).get("name")));
        hostFrames.await(f -> players(f).size() > 1 && "Alicia".equals(players(f).get(0).get("name")));

        assertThat(bobFrames.queue.isEmpty(), is(true));
    }

    @Test
    void lastHumanLeaves_theLobbyIsRemovedThroughTheEvictionListeners() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessionService.addBot(id, room.hostToken());
        connectionRegistry.bind("conn-leave-it", new org.kevinkib.cardgames.sessionmanagement.presence.domain.SeatClaim(
                id, room.hostToken()));
        StompSession host = connect();
        subscribeSeat(host, room.gameId(), room.hostToken());

        send(host, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", room.hostToken()));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (connectionRegistry.claimOf("conn-leave-it").isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(connectionRegistry.claimOf("conn-leave-it").isPresent(), is(false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sessionService.joinRoom(id, "Bob"));
    }
}
