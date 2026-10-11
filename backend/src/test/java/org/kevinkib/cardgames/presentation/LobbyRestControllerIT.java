package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyRestControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    private HttpResponse<String> post(String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/lobby"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void postLobby_returnsGameIdAndTokenToTheCallerOnly_neverOnTheGameTopic() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        List<String> frames = new CopyOnWriteArrayList<>();
        try {
            session.subscribe("/topic/game", new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) { frames.add((String) payload); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (broker.getSubscriptionRegistry().findSubscriptions(topicProbe()).isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }

            HttpResponse<String> response = post("{\"gameType\":\"bataille-corse\",\"name\":\"Alice\"}");

            assertThat(response.statusCode(), is(200));
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            assertThat(body.get("gameId"), notNullValue());
            assertThat(body.get("token"), notNullValue());
            Thread.sleep(500);
            assertThat(frames, is(empty()));
        } finally {
            session.disconnect();
        }
    }

    private static org.springframework.messaging.Message<byte[]> topicProbe() {
        org.springframework.messaging.simp.SimpMessageHeaderAccessor accessor =
                org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                        org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        accessor.setDestination("/topic/game");
        return org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],
                accessor.getMessageHeaders());
    }

    @Test
    void postLobby_withAnUnknownGameType_answers404() throws Exception {
        assertThat(post("{\"gameType\":\"chess\"}").statusCode(), is(404));
        assertThat(post("{}").statusCode(), is(404));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aCreatedBatailleCorseRoom_canBeJoinedAndRehydratedOverHttp() throws Exception {
        Map<String, Object> created = objectMapper.readValue(
                post("{\"gameType\":\"bataille-corse\",\"name\":\"Alice\"}").body(), Map.class);
        String gameId = (String) created.get("gameId");
        String hostToken = (String) created.get("token");
        HttpClient http = HttpClient.newHttpClient();

        HttpResponse<String> lobby = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/game/" + gameId + "?token=" + hostToken))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(lobby.statusCode(), is(200));
        assertThat(objectMapper.readValue(lobby.body(), Map.class).get("canStart"), is(false));

        HttpResponse<String> joined = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/game/" + gameId + "/join"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Bob\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(joined.statusCode(), is(200));

        lobby = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/game/" + gameId + "?token=" + hostToken))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(objectMapper.readValue(lobby.body(), Map.class).get("canStart"), is(true));

        HttpResponse<String> forbidden = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/game/" + gameId + "?token=nope"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(forbidden.statusCode(), is(403));
    }
}
