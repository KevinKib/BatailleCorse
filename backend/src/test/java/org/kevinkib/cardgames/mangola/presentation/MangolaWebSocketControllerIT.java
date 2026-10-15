package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaResult;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaLayouts;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.Sowing;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Mangola over a real WebSocket with two clients: lobby creation, join, options, start, the hidden placement
 * (no draft of the other seat in any frame), the reveal, a capture, a refused sow answered to the sender alone
 * and a whole game from a checkerboard placement played to its end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MangolaWebSocketControllerIT {

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

    /** A started two-player table: the host starts (option {@code firstPlayer=host}), both subscribed to their seat. */
    private final class Table {
        final String gameId;
        final String[] tokens = new String[2];
        final StompSession[] stomp = new StompSession[2];
        final Frames[] frames = new Frames[2];

        @SuppressWarnings("unchecked")
        Table() throws Exception {
            HttpResponse<String> created = post("/api/lobby", "{\"gameType\":\"mangola\",\"name\":\"Alice\"}");
            assertThat(created.statusCode(), is(200));
            Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
            gameId = (String) room.get("gameId");
            tokens[0] = (String) room.get("token");
            assertThat(sessionService.gameType(new GameId(gameId)), is("mangola"));
            sessionService.updateOptions(new GameId(gameId), tokens[0], Map.of("firstPlayer", "host"));

            HttpResponse<String> joined = post("/api/mangola/game/" + gameId + "/join", "{\"name\":\"Bob\"}");
            assertThat(joined.statusCode(), is(200));
            tokens[1] = (String) objectMapper.readValue(joined.body(), Map.class).get("token");

            for (int seat = 0; seat < 2; seat++) {
                stomp[seat] = client.connect();
                frames[seat] = client.subscribeSeat(stomp[seat], gameId, tokens[seat]);
            }
            send(0, "/mangola/start", body(tokens[0]));
            for (int seat = 0; seat < 2; seat++) {
                Map<String, Object> start = frames[seat].await(f -> "START".equals(f.get("eventType")));
                assertThat(state(start).get("phase"), is("PLACEMENT"));
            }
        }

        void send(int seat, String destination, Map<String, Object> body) {
            client.sendApp(stomp[seat], destination, body);
        }

        Map<String, Object> body(String token, Object... more) {
            return StompTestClient.body(gameId, token, more);
        }

        /** Sends a command and waits until both seats have seen its successful event. */
        void command(int seat, String destination, String expectedEvent, Object... more) throws Exception {
            send(seat, destination, body(tokens[seat], more));
            for (int s = 0; s < 2; s++) {
                frames[s].await(f -> expectedEvent.equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
            }
        }

        void arrange(int seat, int[] counts) throws Exception {
            command(seat, "/mangola/arrange", "LAYOUT_CHANGED", "counts", Collections.unmodifiableList(
                    java.util.Arrays.stream(counts).boxed().toList()));
        }

        Map<String, Object> rehydrate(int seat) throws Exception {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/mangola/game/"
                            + gameId + "?token=" + tokens[seat])).GET().build(),
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> board(Map<String, Object> frame) {
        return (List<Integer>) state(frame).get("board");
    }

    /** Seat 0 (side A): a2 2, c2 1 and 29 seeds on the outer row, in the order of its loop. */
    private static int[] captureLayoutA() {
        return new int[]{2, 0, 1, 0, 0, 0, 0, 0, 4, 4, 4, 4, 4, 3, 3, 3};
    }

    /** Seat 1 (side B): c4 3, c3 2, and the rest far from the action (loop order a4..h4, h3..a3). */
    private static int[] captureLayoutB() {
        return new int[]{10, 0, 3, 0, 0, 0, 0, 17, 0, 0, 0, 0, 0, 2, 0, 0};
    }

    /** A side of a board as the counts of its loop, the shape of a placement draft. */
    private static int[] loopCounts(MangolaBoard board, Side side) {
        return MangolaGeometry.loop(side).stream().mapToInt(board::seeds).toArray();
    }

    // ------------------------------------------------------------------ tests

    @Test
    @SuppressWarnings("unchecked")
    void thePlacementIsHiddenThenRevealedToBothSeatsAndAMoveCapturesTheOpponentsSeeds() throws Exception {
        Table table = new Table();

        table.arrange(0, captureLayoutA());
        table.arrange(1, captureLayoutB());
        // while hidden, neither seat's frames carry the other's draft; seat 1's 17 on h4 and 10 on a4 are secret
        for (Map<String, Object> frame : table.frames[0].all) {
            List<Integer> shown = board(frame).subList(16, 32);
            assertThat(shown.stream().allMatch(c -> c == null), is(true));
        }
        for (Map<String, Object> frame : table.frames[1].all) {
            List<Integer> shown = board(frame).subList(0, 16);
            assertThat(shown.stream().allMatch(c -> c == null), is(true));
        }

        table.command(0, "/mangola/confirmLayout", "LAYOUT_CONFIRMED");
        List<Map<String, Object>> players = (List<Map<String, Object>>) table.rehydrate(1).get("players");
        assertThat(players.get(0).get("ready"), is(true));
        assertThat(table.rehydrate(1).get("board") != null, is(true));
        assertThat(((List<Integer>) table.rehydrate(1).get("board")).subList(0, 16).stream().allMatch(c -> c == null), is(true));

        table.command(1, "/mangola/confirmLayout", "LAYOUTS_REVEALED");
        for (int seat = 0; seat < 2; seat++) {
            assertThat(table.rehydrate(seat).get("phase"), is("PLAY"));
            assertThat(table.rehydrate(seat).get("board"), is(table.rehydrate(0).get("board")));
        }
        assertThat(board(table.frames[1].all.get(table.frames[1].all.size() - 1)).get(26), is(3));

        // the opponent's moves are refused to the sender alone
        table.send(1, "/mangola/sow", table.body(table.tokens[1], "pit", MangolaGeometry.pit("h4")));
        table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")) && "SOWN".equals(f.get("eventType")));
        assertThat(table.refusals(1), is(1L));
        assertThat(table.refusals(0), is(0L));

        // E3: a2 captures c3 + c4 (5 seeds)
        table.send(0, "/mangola/sow", table.body(table.tokens[0], "pit", MangolaGeometry.pit("a2")));
        for (int seat = 0; seat < 2; seat++) {
            Map<String, Object> sown = table.frames[seat].await(f -> "SOWN".equals(f.get("eventType"))
                    && Boolean.TRUE.equals(f.get("success")));
            Map<String, Object> data = (Map<String, Object>) sown.get("eventData");
            assertThat(data.get("captured"), is(5));
            assertThat(((List<Map<String, Object>>) data.get("trace")).get(0).get("type"), is("PICK"));
            assertThat(board(sown).get(MangolaGeometry.pit("c3")), is(0));
            assertThat(board(sown).get(MangolaGeometry.pit("c4")), is(0));
            assertThat(state(sown).get("currentSeat"), is(1));
            assertThat(((Map<String, Object>) state(sown).get("lastMove")).get("pit"), is(8));
        }
    }

    @Test
    void aDraftCanBeTakenBackAndTheSamePlayerStillCannotSowDuringPlacement() throws Exception {
        Table table = new Table();
        table.arrange(0, MangolaTable.twoPerPit());
        table.command(0, "/mangola/confirmLayout", "LAYOUT_CONFIRMED");

        table.send(0, "/mangola/arrange", table.body(table.tokens[0], "counts",
                java.util.Arrays.stream(MangolaTable.allOnFirst()).boxed().toList()));
        table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")) && "LAYOUT_CHANGED".equals(f.get("eventType")));

        table.command(0, "/mangola/editLayout", "LAYOUT_WITHDRAWN");
        table.send(0, "/mangola/sow", table.body(table.tokens[0], "pit", 8));
        table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")) && "SOWN".equals(f.get("eventType")));
        assertThat(table.refusals(1), is(0L));
        assertThat(table.rehydrate(0).get("phase"), is("PLACEMENT"));
    }

    @Test
    void aSeatCannotCommandWithTheOtherSeatsTokenNorWithoutOne() throws Exception {
        Table table = new Table();

        table.send(1, "/mangola/arrange", table.body("forged", "counts",
                java.util.Arrays.stream(MangolaTable.allOnFirst()).boxed().toList()));
        table.send(1, "/mangola/confirmLayout", table.body(null));
        table.arrange(0, MangolaTable.twoPerPit());

        assertThat(table.rehydrate(0).get("version"), is(1));
        assertThat(table.refusals(1), is(0L));
    }

    /**
     * A whole game from the checkerboard placed by both seats, the moves picked by a replica of the domain (the most seeds on the
     * mover's side after the move): every frame must match the replica, and the end must be the replica's.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aWholeCheckerboardGameIsPlayedToItsEndAndBroadcastToBothSeats() throws Exception {
        Table table = new Table();
        PlayerId seatA = new PlayerId(0);
        MangolaRules rules = MangolaRules.OWNER;
        MangolaBoard checkerboard = MangolaLayouts.checkerboard();
        table.arrange(0, loopCounts(checkerboard, Side.A));
        table.arrange(1, loopCounts(checkerboard, Side.B));
        table.command(0, "/mangola/confirmLayout", "LAYOUT_CONFIRMED");
        table.command(1, "/mangola/confirmLayout", "LAYOUTS_REVEALED");
        Mangola replica = Mangola.fromLayout(GameId.generate(), rules, seatA, checkerboard);

        int plies = 0;
        while (!replica.isFinished() && plies++ < 400) {
            PlayerId mover = replica.currentSeat();
            Side side = replica.sideOf(mover);
            int best = -1;
            int bestSeeds = -1;
            for (int pit : replica.legalPits(mover)) {
                int seeds = Sowing.sow(replica.board(), side, pit, rules).board().sideTotal(side);
                if (seeds > bestSeeds) {
                    best = pit;
                    bestSeeds = seeds;
                }
            }
            replica.sow(mover, best);
            table.send(mover.id(), "/mangola/sow", table.body(table.tokens[mover.id()], "pit", best));
            for (int seat = 0; seat < 2; seat++) {
                Map<String, Object> frame = table.frames[seat].await(f -> Boolean.TRUE.equals(f.get("success"))
                        && ("SOWN".equals(f.get("eventType")) || "GAME_OVER".equals(f.get("eventType"))));
                assertThat(board(frame), is(replica.board().toList()));
            }
        }

        assertThat(replica.isFinished(), is(true));
        MangolaResult result = replica.result();
        Map<String, Object> last = table.rehydrate(0);
        Map<String, Object> outcome = (Map<String, Object>) last.get("outcome");
        assertThat(outcome.get("status"), is("FINISHED"));
        assertThat(outcome.get("reason"), is(result.reason().name()));
        assertThat(outcome.get("winner"), is(result.winner() == null ? null : result.winner().id()));
        assertThat(last.get("phase"), is("FINISHED"));
        assertThat(((List<Integer>) last.get("legalPits")).isEmpty(), is(true));
        assertThat(table.refusals(0) + table.refusals(1), is(0L));
        assertThat(last.get("lastMove") != null, is(true));
        assertThat(last.get("draftOnlyEndless"), is(false));
    }
}
