package org.kevinkib.cardgames.presentation.support;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * STOMP client for the WebSocket integration tests: connects raw-JSON sessions to {@code /ws}, subscribes
 * (returning the {@link Frames} received), sends commands and disconnects everything at the end.
 *
 * <p>Waiting never uses a fixed sleep: {@link #subscribe} polls the broker's own subscription registry until
 * the subscription exists (the simple broker sends no RECEIPT), and {@link Frames} waits on the frames
 * received, bounded by a deadline.
 */
public final class StompTestClient {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final int port;
    private final ObjectMapper objectMapper;
    private final SimpleBrokerMessageHandler broker;
    private final List<StompSession> sessions = new ArrayList<>();

    public StompTestClient(int port, ObjectMapper objectMapper, SimpleBrokerMessageHandler broker) {
        this.port = port;
        this.objectMapper = objectMapper;
        this.broker = broker;
    }

    /** Opens a session; it is disconnected by {@link #disconnectAll}. */
    public StompSession connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        // Raw JSON strings in both directions: let the string converter accept application/json too.
        client.setMessageConverter(new StringMessageConverter() {
            {
                addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
            }
        });
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { })
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    public void disconnectAll() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        sessions.clear();
    }

    /**
     * Subscribes and returns what the subscription receives, only once the broker has registered it, so a
     * command sent right after cannot be broadcast before the subscription exists. A {@code null} token sends
     * no {@code token} header (public topics).
     */
    public Frames subscribe(StompSession session, String destination, String token) {
        Frames frames = new Frames(TIMEOUT);
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        if (token != null) {
            headers.add("token", token);
        }
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.accept(objectMapper.readValue((String) payload, Map.class));
            }
        });
        awaitBrokerSubscription(destination);
        return frames;
    }

    public Frames subscribe(StompSession session, String destination) {
        return subscribe(session, destination, null);
    }

    /** Subscribes to a seat's private topic {@code /topic/game/{gameId}/seat/{token}}. */
    public Frames subscribeSeat(StompSession session, String gameId, String token) {
        return subscribe(session, "/topic/game/" + gameId + "/seat/" + token, token);
    }

    /** Sends a JSON body to the destination, as is. */
    public void send(StompSession session, String destination, Map<String, Object> body) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, objectMapper.writeValueAsString(body));
    }

    /** Sends a JSON body to an application destination: {@code /app} is prepended. */
    public void sendApp(StompSession session, String destination, Map<String, Object> body) {
        send(session, "/app" + destination, body);
    }

    /** {@code {gameId, token, key: value, ...}} from key / value pairs. */
    public static Map<String, Object> body(String gameId, String token, Object... more) {
        Map<String, Object> map = new HashMap<>();
        map.put("gameId", gameId);
        map.put("token", token);
        for (int i = 0; i < more.length; i += 2) {
            map.put((String) more[i], more[i + 1]);
        }
        return map;
    }

    /** Whether the broker has a subscription for the destination right now. */
    public boolean hasBrokerSubscription(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return !broker.getSubscriptionRegistry().findSubscriptions(probe).isEmpty();
    }

    /**
     * The simple broker sends no RECEIPT frames, and SUBSCRIBE / SEND are handled on separate threads, so an
     * action sent right after a SUBSCRIBE could be broadcast before the subscription exists. Poll the broker's
     * own registry (bounded by a deadline, no fixed sleep) until the subscription is registered.
     */
    public void awaitBrokerSubscription(String destination) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!hasBrokerSubscription(destination)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Subscription to " + destination + " was not registered by the broker");
            }
            Thread.onSpinWait();
        }
    }

    /** The frames a subscription receives. */
    public static final class Frames {
        private final BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();

        /** Frames an earlier {@link #await} skipped: still searchable by {@link #awaitAny}. */
        private final List<Map<String, Object>> skipped = new CopyOnWriteArrayList<>();

        /** Every frame received, in arrival order, whatever the waits consumed. */
        public final List<Map<String, Object>> all = new CopyOnWriteArrayList<>();

        private final Duration timeout;

        public Frames(Duration timeout) {
            this.timeout = timeout;
        }

        public void accept(Map<String, Object> frame) {
            all.add(frame);
            queue.add(frame);
        }

        /** The next frame matching the predicate, in arrival order; the frames before it are skipped. */
        public Map<String, Object> await(Predicate<Map<String, Object>> predicate) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                Map<String, Object> frame = queue.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null && predicate.test(frame)) {
                    return frame;
                }
                if (frame != null) {
                    skipped.add(frame);
                }
            }
            throw new AssertionError("No matching frame received");
        }

        /** Like {@link #await}, but also looks at the frames already skipped, whatever their arrival order. */
        public Map<String, Object> awaitAny(Predicate<Map<String, Object>> predicate) throws Exception {
            for (Map<String, Object> frame : skipped) {
                if (predicate.test(frame)) {
                    return frame;
                }
            }
            return await(predicate);
        }

        /** The frames received and not yet consumed by a wait. */
        public List<Map<String, Object>> drain() {
            List<Map<String, Object>> drained = new ArrayList<>();
            queue.drainTo(drained);
            return drained;
        }
    }
}
