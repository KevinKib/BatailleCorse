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
import org.springframework.messaging.simp.stomp.StompSession;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.kevinkib.cardgames.presentation.support.StompTestClient.body;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end test of the Bataille Corse STOMP protocol over a real WebSocket: the game is created over
 * /app/create (answered on the lobby topic /topic/game, with one token per seat in SOLO mode), then game
 * events are received on /topic/game/{id} after sending token-authenticated actions to /app/send and /app/slap.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatailleCorseWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

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
    void givenGameAndOtherSeatToken_whenSendOutOfTurn_thenGameTopicReceivesErrorAndStateIsUnchanged() throws Exception {
        Map<String, Object> created = createGame();
        String gameId = gameIdOf(created);
        String tokenOfSecondPlayer = tokenOf(created, 1);
        Frames events = subscribeToGame(gameId);

        client.send(stompSession, "/app/send", body(gameId, tokenOfSecondPlayer));

        Map<String, Object> event = events.await(f -> true);
        assertThat(event.get("success"), is(false));
        assertThat(event.get("eventType"), is("SEND"));
        Map<String, Object> state = (Map<String, Object>) event.get("state");
        assertThat((List<?>) ((Map<String, Object>) state.get("pile")).get("cards"), empty());
    }

    @Test
    void givenGame_whenSendWithUnknownToken_thenGameTopicReceivesErrorResponse() throws Exception {
        String gameId = gameIdOf(createGame());
        Frames events = subscribeToGame(gameId);

        client.send(stompSession, "/app/send", body(gameId, UUID.randomUUID().toString()));

        Map<String, Object> event = events.await(f -> true);
        assertThat(event.get("success"), is(false));
        assertThat(event.get("eventType"), is("SEND"));
    }

    @Test
    void givenEmptyPile_whenSlap_thenGameTopicReceivesSlapEvent() throws Exception {
        Map<String, Object> created = createGame();
        String gameId = gameIdOf(created);
        Frames events = subscribeToGame(gameId);

        client.send(stompSession, "/app/slap", body(gameId, tokenOf(created, 0)));

        Map<String, Object> event = events.await(f -> true);
        assertThat(event.get("eventType"), is("SLAP"));
        assertThat(event.get("state"), notNullValue());
    }

    // ---- helpers ----

    private Map<String, Object> createGame() throws Exception {
        Frames created = client.subscribe(stompSession, "/topic/game");
        client.send(stompSession, "/app/create", Map.of("mode", "SOLO"));
        return created.await(f -> true);
    }

    private Frames subscribeToGame(String gameId) {
        return client.subscribe(stompSession, "/topic/game/" + gameId);
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
