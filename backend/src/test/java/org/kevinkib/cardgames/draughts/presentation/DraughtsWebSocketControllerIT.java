package org.kevinkib.cardgames.draughts.presentation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import tools.jackson.databind.ObjectMapper;

/**
 * Draughts over a real WebSocket with two clients: lobby creation, join, start, a few moves, a refused move answered
 * to the sender alone, a draw offer and a resignation through the shared forfeit, and rehydration through REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DraughtsWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

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

    /** A started two-player table with the host as White: both clients subscribed to their private seat topic. */
    private final class Table {
        final String gameId;
        final String[] tokens = new String[2];
        final StompSession[] stomp = new StompSession[2];
        final Frames[] frames = new Frames[2];

        @SuppressWarnings("unchecked")
        Table() throws Exception {
            HttpResponse<String> created = post("/api/lobby", "{\"gameType\":\"draughts\",\"name\":\"Alice\"}");
            assertThat(created.statusCode(), is(200));
            Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
            gameId = (String) room.get("gameId");
            tokens[0] = (String) room.get("token");
            sessionService.updateOptions(new GameId(gameId), tokens[0], Map.of("hostColour", "white"));
            assertThat(sessionService.gameType(new GameId(gameId)), is("draughts"));

            HttpResponse<String> joined = post("/api/draughts/game/" + gameId + "/join", "{\"name\":\"Bob\"}");
            assertThat(joined.statusCode(), is(200));
            tokens[1] = (String) objectMapper.readValue(joined.body(), Map.class).get("token");

            for (int seat = 0; seat < 2; seat++) {
                stomp[seat] = client.connect();
                frames[seat] = client.subscribeSeat(stomp[seat], gameId, tokens[seat]);
            }
            send(0, "/draughts/start", body(tokens[0]));
            for (int seat = 0; seat < 2; seat++) {
                Map<String, Object> start = frames[seat].await(f -> "START".equals(f.get("eventType")));
                assertThat(state(start).get("phase"), is("PLAY"));
            }
        }

        void send(int seat, String destination, Map<String, Object> body) {
            client.sendApp(stomp[seat], destination, body);
        }

        Map<String, Object> body(String token, Object... more) {
            return StompTestClient.body(gameId, token, more);
        }

        /** Plays a move and waits until both seats have seen the event. */
        void move(int seat, int from, int to, List<Integer> captures, String expectedEvent) throws Exception {
            send(seat, "/draughts/move", body(tokens[seat], "from", from, "to", to, "captures", captures));
            for (int s = 0; s < 2; s++) {
                frames[s].await(f -> expectedEvent.equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
            }
        }

        Map<String, Object> rehydrate(int seat) throws Exception {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/draughts/game/" + gameId
                            + "?token=" + tokens[seat])).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode(), is(200));
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            return parsed;
        }

        long refusals(int seat) {
            return frames[seat].all.stream().filter(f -> Boolean.FALSE.equals(f.get("success"))).count();
        }
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> startFrame(Table table, int seat) {
        return table.frames[seat].all.stream().filter(f -> "START".equals(f.get("eventType"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFirstMovesAreBroadcastToBothSeatsWithTheirLegalMovesOnTurn() throws Exception {
        Table table = new Table();
        assertThat(((List<Object>) state(startFrame(table, 0)).get("legalMoves")), hasSize(9));
        assertThat(((List<Object>) state(startFrame(table, 1)).get("legalMoves")), hasSize(0));

        table.move(0, 32, 28, List.of(), "MOVE");
        Map<String, Object> afterWhite = state(table.frames[1].all.stream()
                .filter(f -> "MOVE".equals(f.get("eventType"))).findFirst().orElseThrow());
        assertThat(afterWhite.get("currentSeat"), is(1));
        assertThat(afterWhite.get("history"), is(List.of("32-28")));
        assertThat(((List<Object>) afterWhite.get("legalMoves")).isEmpty(), is(false));

        table.move(1, 17, 22, List.of(), "MOVE");
        Map<String, Object> rest = table.rehydrate(0);
        assertThat(rest.get("history"), is(List.of("32-28", "17-22")));
        assertThat(((Map<String, Object>) rest.get("lastMove")).get("to"), is(22));
        assertThat(((List<Object>) rest.get("board")).get(27), is("w"));
        assertThat(((Map<String, Object>) rest.get("outcome")).get("status"), is("ONGOING"));
    }

    @Test
    void aMoveOutOfTurnOrIllegalIsRefusedToItsSenderOnlyAndNothingChanges() throws Exception {
        Table table = new Table();
        int version = (Integer) table.rehydrate(0).get("version");

        table.send(1, "/draughts/move", table.body(table.tokens[1], "from", 17, "to", 22, "captures", List.of()));
        Map<String, Object> notYourTurn = table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")));
        assertThat(notYourTurn.get("message"), is("draughts.errors.notYourTurn"));
        table.send(0, "/draughts/move", table.body(table.tokens[0], "from", 32, "to", 37, "captures", List.of()));
        Map<String, Object> illegal = table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")));
        assertThat(illegal.get("message"), is("draughts.errors.illegalMove"));

        assertThat(table.rehydrate(0).get("version"), is(version));
        assertThat(table.refusals(0), is(1L));
        assertThat(table.refusals(1), is(1L));
    }

    @Test
    void aSeatCannotPlayWithAForgedOrMissingToken() throws Exception {
        Table table = new Table();

        table.send(1, "/draughts/move", table.body("forged", "from", 32, "to", 28, "captures", List.of()));
        table.send(1, "/draughts/move", table.body(null, "from", 32, "to", 28, "captures", List.of()));
        table.move(0, 32, 28, List.of(), "MOVE");

        assertThat(table.rehydrate(0).get("version"), is(1));
        assertThat(table.refusals(1), is(0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDrawIsOfferedDeclinedAndOfferedAgainAfterAMoveThenAccepted() throws Exception {
        Table table = new Table();

        table.send(0, "/draughts/offerDraw", table.body(table.tokens[0]));
        for (int s = 0; s < 2; s++) {
            Map<String, Object> offered = table.frames[s].await(f -> "DRAW_OFFERED".equals(f.get("eventType")));
            assertThat(((Map<String, Object>) offered.get("eventData")).get("seat"), is(0));
        }
        assertThat(table.rehydrate(1).get("drawOffer"), is(Map.of("by", 0)));
        table.send(1, "/draughts/declineDraw", table.body(table.tokens[1]));
        table.frames[0].await(f -> "DRAW_DECLINED".equals(f.get("eventType")));
        assertThat(table.rehydrate(1).containsKey("drawOffer"), is(false));

        table.send(0, "/draughts/offerDraw", table.body(table.tokens[0]));
        table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")));

        table.move(0, 32, 28, List.of(), "MOVE");
        table.send(0, "/draughts/offerDraw", table.body(table.tokens[0]));
        table.frames[1].await(f -> "DRAW_OFFERED".equals(f.get("eventType")) && f.get("state") != null
                && state(f).get("drawOffer") != null && ((Integer) state(f).get("version")) > 2);
        table.send(1, "/draughts/acceptDraw", table.body(table.tokens[1]));

        for (int seat = 0; seat < 2; seat++) {
            Map<String, Object> over = table.frames[seat].await(f -> "GAME_OVER".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(over).get("outcome");
            assertThat(outcome.get("status"), is("FINISHED"));
            assertThat(outcome.get("winner"), is(nullValue()));
            assertThat(outcome.get("reason"), is("DRAW_AGREED"));
        }
        assertThat(table.rehydrate(0).get("phase"), is("FINISHED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resigningThroughTheSharedForfeitGivesTheOtherSeatTheWin() throws Exception {
        Table table = new Table();
        table.move(0, 32, 28, List.of(), "MOVE");

        table.send(0, "/forfeit", table.body(table.tokens[0]));

        for (int seat = 0; seat < 2; seat++) {
            Map<String, Object> forfeit = table.frames[seat].await(f -> "FORFEIT".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(forfeit).get("outcome");
            assertThat(outcome.get("winner"), is(1));
            assertThat(outcome.get("reason"), is("FORFEIT"));
        }
        assertThat(table.rehydrate(1).get("phase"), is("FINISHED"));
    }
}
