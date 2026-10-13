package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.kevinkib.cardgames.presentation.support.StompTestClient.body;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end test of the Bataille Corse STOMP protocol over a real WebSocket, on the room model: the lobby is
 * created over {@code POST /api/lobby} (the token goes back to the caller only), a second human joins over REST,
 * the host starts it on /app/bataillecorse/start, then game events are received on /topic/game/{id} after
 * sending token-authenticated actions to /app/send and /app/slap.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatailleCorseWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    private final RestTemplate rest = new RestTemplate();
    private StompTestClient client;
    private StompSession stompSession;

    @BeforeEach
    void setUp() throws Exception {
        client = new StompTestClient(port, objectMapper, broker);
        stompSession = client.connect();
    }

    @AfterEach
    void tearDown() {
        client.disconnectAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenTwoSeatsTaken_whenTheHostStarts_thenTheGameTopicReceivesTheDealtGame() throws Exception {
        Started started = startGame();

        Map<String, Object> response = started.start();
        assertThat(response.get("success"), is(true));
        assertThat(response.get("eventType"), is("START"));

        Map<String, Object> state = (Map<String, Object>) response.get("state");
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
    void eachHumanSeat_getsItsOwnToken() {
        Started started = startGame();

        assertThat(started.hostToken(), not(equalTo(started.guestToken())));
        UUID.fromString(started.hostToken());
        UUID.fromString(started.guestToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenGameAndSeatToken_whenSend_thenGameTopicReceivesSendEventAndTurnPasses() throws Exception {
        Started started = startGame();
        started.start();
        String gameId = started.gameId();
        String token = started.hostToken();
        Frames events = subscribeToGame(gameId);

        client.send(stompSession, "/app/send", body(gameId, token));

        Map<String, Object> event = events.await(f -> true);
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
    void givenGameAndOtherSeatToken_whenSendOutOfTurn_thenOnlyTheSenderReceivesTheErrorWithTheUnchangedState() throws Exception {
        Started started = startGame();
        started.start();
        String gameId = started.gameId();
        String tokenOfSecondPlayer = started.guestToken();
        Frames topic = subscribeToGame(gameId);
        Frames guest = client.subscribeSeat(stompSession, gameId, tokenOfSecondPlayer);
        Frames host = client.subscribeSeat(stompSession, gameId, started.hostToken());

        client.send(stompSession, "/app/send", body(gameId, tokenOfSecondPlayer));

        Map<String, Object> event = guest.await(f -> "SEND".equals(f.get("eventType")));
        assertThat(topic.all, empty());
        assertThat(host.all, empty());
        assertThat(event.get("success"), is(false));
        assertThat(event.get("eventType"), is("SEND"));
        Map<String, Object> state = (Map<String, Object>) event.get("state");
        assertThat((List<?>) ((Map<String, Object>) state.get("pile")).get("cards"), empty());
    }

    @Test
    void givenGame_whenSendWithUnknownToken_thenNothingIsSentToAnyone() throws Exception {
        Started started = startGame();
        started.start();
        String gameId = started.gameId();
        Frames topic = subscribeToGame(gameId);
        Frames host = client.subscribeSeat(stompSession, gameId, started.hostToken());

        client.send(stompSession, "/app/send", body(gameId, UUID.randomUUID().toString()));
        // A later legitimate command proves the first one was processed (and answered with nothing).
        client.send(stompSession, "/app/send", body(gameId, started.hostToken()));

        Map<String, Object> event = topic.await(f -> true);
        assertThat(event.get("success"), is(true));
        assertThat(topic.all, hasSize(1));
        assertThat(host.all, empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenEmptyPile_whenSlap_thenOnlyTheSenderReceivesTheRefusalWithItsState() throws Exception {
        Started started = startGame();
        started.start();
        String gameId = started.gameId();
        Frames topic = subscribeToGame(gameId);
        Frames host = client.subscribeSeat(stompSession, gameId, started.hostToken());

        client.send(stompSession, "/app/slap", body(gameId, started.hostToken()));

        Map<String, Object> event = host.await(f -> "SLAP".equals(f.get("eventType")));
        assertThat(event.get("success"), is(false));
        assertThat(event.get("state"), notNullValue());
        assertThat(topic.all, empty());
    }

    // ---- helpers ----

    /** A lobby with the host and a second human seated; {@link #start()} has the host start it. */
    private record Started(String gameId, String hostToken, String guestToken, Starter starter) {
        Map<String, Object> start() throws Exception {
            return starter.start(this);
        }
    }

    @FunctionalInterface
    private interface Starter {
        Map<String, Object> start(Started room) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private Started startGame() {
        Map<String, String> lobby = rest.postForObject(url("/api/lobby"),
                json("{\"gameType\":\"bataille-corse\",\"name\":\"Alice\"}"), Map.class);
        String gameId = lobby.get("gameId");
        Map<String, Object> joined = rest.postForObject(url("/api/game/" + gameId + "/join"),
                json("{\"name\":\"Bob\"}"), Map.class);
        return new Started(gameId, lobby.get("token"), (String) joined.get("token"), room -> {
            Frames events = subscribeToGame(room.gameId());
            client.send(stompSession, "/app/bataillecorse/start", body(room.gameId(), room.hostToken()));
            return events.await(f -> true);
        });
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Frames subscribeToGame(String gameId) {
        return client.subscribe(stompSession, "/topic/game/" + gameId);
    }
}
