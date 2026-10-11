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

/** The host edits the game options of a lobby over a real WebSocket; the guest sees the change live. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyOptionsIT {

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

    private void setOptions(StompSession session, String gameId, String token, Map<String, String> values) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/lobby/setOptions");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, objectMapper.writeValueAsString(
                Map.of("gameId", gameId, "token", token, "values", values)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> options(Map<String, Object> frame) {
        Map<String, Object> state = state(frame);
        return state == null ? Map.of() : (Map<String, String>) state.get("options");
    }

    @Test
    void hostEditsOptions_guestSeesTheChangeLive() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = connect();
        StompSession guest = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = subscribeSeat(guest, room.gameId(), guestToken);

        setOptions(host, room.gameId(), room.hostToken(), Map.of("claimMode", "suit"));

        Map<String, Object> hostSees = hostFrames.await(f -> "suit".equals(options(f).get("claimMode")));
        Map<String, Object> guestSees = guestFrames.await(f -> "suit".equals(options(f).get("claimMode")));
        assertThat(state(hostSees).get("canEditOptions"), is(true));
        assertThat(state(guestSees).get("canEditOptions"), is(false));
        assertThat(((List<?>) state(guestSees).get("optionDescriptors")).size(), is(2));
    }

    @Test
    void guestEditingOptions_isRefusedToTheGuestOnly() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = connect();
        StompSession guest = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = subscribeSeat(guest, room.gameId(), guestToken);

        setOptions(guest, room.gameId(), guestToken, Map.of("claimMode", "suit"));

        guestFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
        // a later accepted change is the next frame the host receives: nothing was broadcast before it
        setOptions(host, room.gameId(), room.hostToken(), Map.of("deckSize", "52"));
        Map<String, Object> hostSees = hostFrames.await(f -> true);
        assertThat(options(hostSees).get("claimMode"), is("rank"));
        assertThat(options(hostSees).get("deckSize"), is("52"));
    }

    @Test
    void invalidValue_isRefusedToTheHostAndNothingChanges() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = connect();
        StompSession guest = connect();
        Frames hostFrames = subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = subscribeSeat(guest, room.gameId(), guestToken);

        setOptions(host, room.gameId(), room.hostToken(), Map.of("claimMode", "bogus"));

        hostFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
        setOptions(host, room.gameId(), room.hostToken(), Map.of("claimMode", "suit"));
        Map<String, Object> guestSees = guestFrames.await(f -> true);
        assertThat(options(guestSees).get("claimMode"), is("suit"));
    }
}
