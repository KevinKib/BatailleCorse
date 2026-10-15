package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.kevinkib.cardgames.presentation.support.StompTestClient.body;

/**
 * President over a real WebSocket: lobby, start, private per-seat states, a few plays, a refused play answered
 * to the sender alone, a forfeit, and reconnection through REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresidentWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionService sessionService;

    private StompTestClient stomp;

    @BeforeEach
    void setUp() {
        stomp = new StompTestClient(port, objectMapper, broker);
    }

    @AfterEach
    void tearDown() {
        stomp.disconnectAll();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rehydrate(String gameId, String token) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/president/game/" + gameId
                        + "?token=" + token)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode(), is(200));
        return objectMapper.readValue(response.body(), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTableOfThreePlaysFromTheLobbyToAForfeit() throws Exception {
        // lobby: the host creates a President room over REST, two guests join
        HttpResponse<String> created = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/lobby"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"gameType\":\"president\",\"name\":\"Alice\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode(), is(200));
        Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
        String gameId = (String) room.get("gameId");
        String[] tokens = {(String) room.get("token"), sessionService.joinRoom(new GameId(gameId), "Bob").token(),
                sessionService.joinRoom(new GameId(gameId), "Carol").token()};
        assertThat(sessionService.gameType(new GameId(gameId)), is("president"));

        StompSession[] sessions = {stomp.connect(), stomp.connect(), stomp.connect()};
        Frames[] frames = new Frames[3];
        for (int i = 0; i < 3; i++) {
            frames[i] = stomp.subscribeSeat(sessions[i], gameId, tokens[i]);
        }

        // start: each seat gets its own 17 or 18 cards and nobody else's
        stomp.sendApp(sessions[0], "/president/start", body(gameId, tokens[0]));
        List<Map<String, Object>> starts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            starts.add(frames[i].await(f -> "START".equals(f.get("eventType"))));
        }
        int total = 0;
        for (int i = 0; i < 3; i++) {
            List<Map<String, Object>> hand = list(state(starts.get(i)).get("hand"));
            total += hand.size();
            assertThat(hand.size() >= 17, is(true));
            for (Map<String, Object> mine : hand) {
                for (int other = 0; other < 3; other++) {
                    if (other != i) {
                        assertThat(objectMapper.writeValueAsString(starts.get(other)),
                                not(containsString("\"name\":\"" + mine.get("name") + "\"")));
                    }
                }
            }
        }
        assertThat(total, is(52));
        int leader = (Integer) state(starts.get(0)).get("currentSeat");
        Map<String, Object> leaderState = state(starts.get(leader));
        assertThat(list(leaderState.get("legalPlays")).isEmpty(), is(false));
        assertThat(state(starts.get((leader + 1) % 3)).get("legalPlays"), is(List.of()));

        // the leader plays its first legal pattern: every seat sees the play, only the leader's hand shrinks
        Map<String, Object> pattern = list(leaderState.get("legalPlays")).get(0);
        assertThat(pattern.get("count"), is(1));
        List<Map<String, Object>> toPlay = list(leaderState.get("hand")).subList(0, 1); // the weakest card
        stomp.sendApp(sessions[leader], "/president/play", body(gameId, tokens[leader], "cards", toPlay));
        for (int i = 0; i < 3; i++) {
            Map<String, Object> played = frames[i].await(f -> "PLAY".equals(f.get("eventType")));
            assertThat(state(played).get("version"), is(1));
            Map<String, Object> trick = (Map<String, Object>) state(played).get("trick");
            assertThat(list(trick.get("plays")), hasSize(1));
        }

        // a seat that is not to act asks to play: the refusal goes to that seat alone
        int notToAct = (leader + 2) % 3;
        Map<String, Object> notMine = list(state(starts.get(notToAct)).get("hand")).get(0);
        stomp.sendApp(sessions[notToAct], "/president/play", body(gameId, tokens[notToAct], "cards", List.of(notMine)));
        Map<String, Object> refused = frames[notToAct].await(f -> Boolean.FALSE.equals(f.get("success")));
        assertThat(refused.get("eventType"), is("PLAY"));
        assertThat(list(state(refused).get("hand")).size() >= 17, is(true));
        for (int i = 0; i < 3; i++) {
            if (i != notToAct) {
                assertThat(frames[i].drain().stream().noneMatch(f -> Boolean.FALSE.equals(f.get("success"))), is(true));
            }
        }

        // the next seat passes
        int next = (leader + 1) % 3;
        stomp.sendApp(sessions[next], "/president/pass", body(gameId, tokens[next]));
        frames[leader].await(f -> "PASS".equals(f.get("eventType")));

        // reconnecting through REST gives the same version as the last broadcast
        Map<String, Object> rest = rehydrate(gameId, tokens[leader]);
        assertThat(rest.get("version"), is(2));
        assertThat(rest.get("gameType"), is("president"));

        // a forfeit: the two others keep playing and receive it
        stomp.sendApp(sessions[2], "/forfeit", body(gameId, tokens[2]));
        Map<String, Object> forfeit = frames[0].await(f -> "FORFEIT".equals(f.get("eventType")));
        assertThat(list(state(forfeit).get("players")), hasSize(2));
        President game = sessionService.getGame(new GameId(gameId), President.class);
        assertThat(game.getPlayerIds().size(), is(2));
        assertThat(game.isFinished(), is(false));
    }
}
