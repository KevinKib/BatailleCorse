package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.ScriptedShoeSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.cards;

/**
 * A room with only the host starts and plays a solo hand against the dealer: {@code minPlayers = 1} is new for the
 * session core. Hand 1 is scripted (17 against a dealer who draws to 21), then the second hand opens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BlackjackSoloIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private ScriptedShoeSource shoes;

    @Autowired
    private SessionService sessionService;

    private StompTestClient client;

    @BeforeEach
    void setUp() {
        client = new StompTestClient(port, objectMapper, broker);
    }

    @AfterEach
    void tearDown() {
        client.disconnectAll();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aHostAloneStartsAndPlaysASoloHand() throws Exception {
        HttpResponse<String> created = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/lobby"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"gameType\":\"blackjack\",\"name\":\"Alice\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode(), is(200));
        Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
        String gameId = (String) room.get("gameId");
        String token = (String) room.get("token");
        shoes.fix(new GameId(gameId), 1, cards("10S 6D 7C 5H 10D"));

        StompSession stomp = client.connect();
        Frames frames = client.subscribeSeat(stomp, gameId, token);
        client.sendApp(stomp, "/blackjack/start", StompTestClient.body(gameId, token));
        Map<String, Object> start = frames.await(f -> "START".equals(f.get("eventType")));
        assertThat(state(start).get("solo"), is(true));
        assertThat(state(start).get("availableActions"), is(List.of("BET")));
        assertThat(sessionService.findGame(new GameId(gameId)).isPresent(), is(true));

        client.sendApp(stomp, "/blackjack/bet", StompTestClient.body(gameId, token, "amount", 10));
        Map<String, Object> deal = frames.await(f -> "DEAL".equals(f.get("eventType")));
        assertThat(state(deal).get("phase"), is("PLAYING"));
        assertThat(state(deal).get("availableActions"), is(List.of("HIT", "STAND", "DOUBLE")));

        client.sendApp(stomp, "/blackjack/stand", StompTestClient.body(gameId, token));
        Map<String, Object> settled = frames.await(f -> "SETTLE".equals(f.get("eventType")));
        Map<String, Object> next = state(settled);
        assertThat(next.get("phase"), is("BETTING"));
        assertThat(next.get("handNumber"), is(2));
        assertThat(next.get("lastBet"), is(10));
        Map<String, Object> lastHand = (Map<String, Object>) next.get("lastHand");
        assertThat(((List<Map<String, Object>>) lastHand.get("seats")).get(0).get("net"), is(-10));
        assertThat(((Map<String, Object>) ((List<Object>) next.get("players")).get(0)).get("stack"), is(90));
    }
}
