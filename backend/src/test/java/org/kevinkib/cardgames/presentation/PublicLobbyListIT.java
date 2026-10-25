package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * The public game list end to end: REST snapshot, live list on {@code /topic/lobbies/kobo}, the private option.
 * It needs the real {@code SessionService.publicLobbies / setPrivate / listener} of B1: the integration chunk
 * removes the {@code @Disabled}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicLobbyListIT {

    private static final String TYPE = KoboFactory.GAME_TYPE;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    private StompTestClient stomp;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        stomp = new StompTestClient(port, objectMapper, broker);
    }

    @AfterEach
    void tearDown() {
        stomp.disconnectAll();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> create(String host, boolean privateGame) throws Exception {
        return objectMapper.readValue(
                post("/api/lobby", "{\"gameType\":\"" + TYPE + "\",\"name\":\"" + host + "\",\"privateGame\":"
                        + privateGame + "}").body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lobbies(Map<String, Object> list) {
        return (List<Map<String, Object>>) list.get("lobbies");
    }

    private static boolean lists(Map<String, Object> list, String gameId) {
        return lobbies(list).stream().anyMatch(l -> gameId.equals(l.get("gameId")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot() throws Exception {
        HttpResponse<String> response = get("/api/lobbies/" + TYPE);
        assertThat(response.statusCode(), is(200));
        return objectMapper.readValue(response.body(), Map.class);
    }

    @Test
    void theSnapshotListsACreatedLobby_withoutAnyTokenOrSeat() throws Exception {
        String host = "Host-" + UUID.randomUUID();
        Map<String, Object> created = create(host, false);

        HttpResponse<String> response = get("/api/lobbies/" + TYPE);

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), containsString(host));
        assertThat(response.body(), not(containsString((String) created.get("token"))));
        assertThat(response.body(), not(containsString("openedAt")));
        assertThat(lists(snapshot(), (String) created.get("gameId")), is(true));
    }

    @Test
    void anUnknownGameTypeAnswers404() throws Exception {
        assertThat(get("/api/lobbies/chess").statusCode(), is(404));
    }

    @Test
    void aLobbyCreatedPrivate_isNotInTheSnapshot() throws Exception {
        Map<String, Object> created = create("Hidden-" + UUID.randomUUID(), true);

        assertThat(lists(snapshot(), (String) created.get("gameId")), is(false));
    }

    @Test
    void aSubscriberReceivesTheNewList_afterAJoin_andNeverATokenInTheFrame() throws Exception {
        Map<String, Object> created = create("Host-" + UUID.randomUUID(), false);
        String gameId = (String) created.get("gameId");
        StompSession viewer = stomp.connect();
        Frames frames = stomp.subscribe(viewer, "/topic/lobbies/" + TYPE);

        post("/api/kobo/game/" + gameId + "/join", "{\"name\":\"Bob\"}");

        Map<String, Object> list = frames.await(f -> lists(f, gameId) && lobbies(f).stream()
                .anyMatch(l -> gameId.equals(l.get("gameId")) && Integer.valueOf(2).equals(l.get("joined"))));
        assertThat(list.get("gameType"), is(TYPE));
        for (Map<String, Object> frame : frames.all) {
            String raw = objectMapper.writeValueAsString(frame);
            assertThat(raw, not(containsString((String) created.get("token"))));
            assertThat(raw, not(containsString("openedAt")));
        }
    }

    @Test
    void whenTheHostMakesTheLobbyPrivate_itDisappearsFromTheLiveList() throws Exception {
        Map<String, Object> created = create("Host-" + UUID.randomUUID(), false);
        String gameId = (String) created.get("gameId");
        String token = (String) created.get("token");
        StompSession host = stomp.connect();
        StompSession viewer = stomp.connect();
        Frames frames = stomp.subscribe(viewer, "/topic/lobbies/" + TYPE);
        assertThat(lists(snapshot(), gameId), is(true));

        stomp.sendApp(host, "/lobby/setPrivate", StompTestClient.body(gameId, token, "privateGame", true));

        frames.await(f -> !lists(f, gameId));
        assertThat(lists(snapshot(), gameId), is(false));
    }
}
