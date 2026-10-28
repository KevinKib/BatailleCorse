package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.presentation.dto.CardDto;
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
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.cards;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.hands;

/**
 * Kilo de merde over a real WebSocket with three clients (profile {@code test}: the deal is scripted, the pass timer
 * is off, the round pause is 0): lobby creation, join, start, a pass step, a valid slap and its race, a double slap
 * refused to its sender alone, the last slapper drawing the only kilo card, and the end of the game.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KiloDeMerdeWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ScriptedDeckSource decks;

    private StompTestClient client;

    @BeforeEach
    void setUp() {
        client = new StompTestClient(port, objectMapper, broker);
    }

    @AfterEach
    void tearDown() {
        client.disconnectAll();
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> cardBody(String code) {
        CardDto dto = CardDto.from(card(code));
        return Map.of("rank", dto.rank(), "suit", dto.suit(), "name", dto.name());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    /** A started three-seat table: the host holds three kings and a queen, the seat on its right the fourth king. */
    private final class Table {
        final String gameId;
        final String[] tokens = new String[3];
        final StompSession[] stomp = new StompSession[3];
        final Frames[] frames = new Frames[3];

        @SuppressWarnings("unchecked")
        Table() throws Exception {
            HttpResponse<String> created = post("/api/lobby", "{\"gameType\":\"kilo-de-merde\",\"name\":\"Alice\"}");
            assertThat(created.statusCode(), is(200));
            Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
            gameId = (String) room.get("gameId");
            tokens[0] = (String) room.get("token");
            for (int seat = 1; seat < 3; seat++) {
                HttpResponse<String> joined = post("/api/kilo-de-merde/game/" + gameId + "/join",
                        "{\"name\":\"P" + seat + "\"}");
                assertThat(joined.statusCode(), is(200));
                tokens[seat] = (String) objectMapper.readValue(joined.body(), Map.class).get("token");
            }
            decks.fixDeal(new GameId(gameId), hands("KS KH KD QC / JS JH QD QH / KC JD JC QS"), cards("AS"));

            for (int seat = 0; seat < 3; seat++) {
                stomp[seat] = client.connect();
                frames[seat] = client.subscribeSeat(stomp[seat], gameId, tokens[seat]);
            }
            client.sendApp(stomp[0], "/kilo-de-merde/start", StompTestClient.body(gameId, tokens[0]));
            for (int seat = 0; seat < 3; seat++) {
                Map<String, Object> start = frames[seat].await(f -> "START".equals(f.get("eventType")));
                assertThat(state(start).get("phase"), is("PASSING"));
            }
        }

        void pass(int seat, String code) {
            client.sendApp(stomp[seat], "/kilo-de-merde/pass", StompTestClient.body(gameId, tokens[seat],
                    "card", cardBody(code)));
        }

        void slap(int seat) {
            client.sendApp(stomp[seat], "/kilo-de-merde/slap", StompTestClient.body(gameId, tokens[seat]));
        }

        void awaitAll(String eventType) throws Exception {
            for (int s = 0; s < 3; s++) {
                frames[s].await(f -> eventType.equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
            }
        }

        Map<String, Object> rehydrate(int seat) throws Exception {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/kilo-de-merde/game/" + gameId
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

    @Test
    @SuppressWarnings("unchecked")
    void theStartGivesEachSeatItsOwnHandOnly() throws Exception {
        Table table = new Table();

        Map<String, Object> mine = state(table.frames[0].all.stream()
                .filter(f -> "START".equals(f.get("eventType"))).findFirst().orElseThrow());
        Map<String, Object> theirs = state(table.frames[1].all.stream()
                .filter(f -> "START".equals(f.get("eventType"))).findFirst().orElseThrow());
        assertThat(((List<Object>) mine.get("hand")).size(), is(4));
        assertThat(mine.get("hand").equals(theirs.get("hand")), is(false));
        assertThat(mine.get("availableActions"), is(List.of("PASS", "SLAP")));
        assertThat(mine.get("myChoice") == null, is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aStepThenARaceRefusesADoubleSlapToItsSenderAloneAndRevealsEveryHand() throws Exception {
        Table table = new Table();

        table.pass(0, "QC");
        table.pass(1, "QD");
        table.pass(2, "KC");
        for (int s = 0; s < 3; s++) {
            Map<String, Object> step = table.frames[s].await(f -> "STEP".equals(f.get("eventType")));
            assertThat(state(step).get("step"), is(2));
        }
        Map<String, Object> hostView = table.rehydrate(0);
        Map<String, Object> received = (Map<String, Object>) hostView.get("lastReceived");
        assertThat(((Map<String, Object>) received.get("card")).get("name"), is("CLUB_KING"));
        assertThat(received.get("from"), is(2));

        table.slap(0); // four kings: a valid slap opens the race
        table.awaitAll("RACE");
        table.slap(0); // a second slap of the same seat is refused to its sender alone
        table.frames[0].await(f -> Boolean.FALSE.equals(f.get("success")));
        assertThat(table.refusals(0), is(1L));
        assertThat(table.refusals(1), is(0L));
        assertThat(table.refusals(2), is(0L));

        table.slap(1);
        for (int s = 0; s < 3; s++) {
            Map<String, Object> over = table.frames[s].await(f -> "ROUND_OVER".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(over).get("lastOutcome");
            assertThat(outcome.get("kind"), is("RACE"));
            assertThat(outcome.get("slapOrder"), is(List.of(0, 1)));
            assertThat(((List<Object>) outcome.get("hands")).size(), is(3));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theLastSlapperTakesTheOnlyKiloCardAndThePileEndsTheGame() throws Exception {
        Table table = new Table();
        table.pass(0, "QC");
        table.pass(1, "QD");
        table.pass(2, "KC");
        table.awaitAll("STEP");

        table.slap(0);
        table.awaitAll("RACE");
        table.slap(1); // seat 2 never slaps: it is the last one

        for (int s = 0; s < 3; s++) {
            Map<String, Object> draw = table.frames[s].await(f -> "DRAW".equals(f.get("eventType")));
            Map<String, Object> data = (Map<String, Object>) draw.get("eventData");
            assertThat(data.get("seat"), is(2));
            assertThat(data.get("kilosAfter"), is(100));
            Map<String, Object> over = table.frames[s].await(f -> "GAME_OVER".equals(f.get("eventType")));
            Map<String, Object> result = (Map<String, Object>) state(over).get("result");
            assertThat(result.get("reason"), is("PILE_EMPTY"));
            List<Map<String, Object>> standings = (List<Map<String, Object>>) result.get("standings");
            assertThat(standings.get(0).get("place"), is(1));
            assertThat(standings.get(1).get("place"), is(1));
            assertThat(standings.get(2).get("place"), is(3));
            assertThat(standings.get(2).get("kilos"), is(100));
        }
        assertThat(table.rehydrate(2).get("phase"), is("FINISHED"));
    }

    @Test
    void aSeatCannotActWithAForgedOrMissingToken() throws Exception {
        Table table = new Table();

        client.sendApp(table.stomp[1], "/kilo-de-merde/slap", StompTestClient.body(table.gameId, "forged"));
        client.sendApp(table.stomp[1], "/kilo-de-merde/pass", StompTestClient.body(table.gameId, null,
                "card", cardBody("QD")));
        table.pass(0, "QC");
        table.awaitAll("PASS_CHOSEN");

        assertThat(table.rehydrate(0).get("version"), is(1));
        assertThat(table.refusals(1), is(0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forfeitingThroughTheSharedForfeitReFormsTheTable() throws Exception {
        Table table = new Table();

        client.sendApp(table.stomp[1], "/forfeit", StompTestClient.body(table.gameId, table.tokens[1]));

        for (int seat = 0; seat < 3; seat += 2) {
            Map<String, Object> forfeit = table.frames[seat].await(f -> "FORFEIT".equals(f.get("eventType")));
            assertThat(state(forfeit).get("phase"), is("FINISHED")); // two seats left: below the minimum
            Map<String, Object> result = (Map<String, Object>) state(forfeit).get("result");
            assertThat(result.get("reason"), is("FORFEIT"));
        }
    }
}
