package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.ninetynine.domain.ScriptedDeckSource;
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
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.cards;

/**
 * 99 over a real WebSocket with two clients (profile {@code test}: the deal is scripted and the round pause is 0):
 * lobby creation, join, start, a play, an unsafe card refused to its sender alone, a bust, the next round, a second
 * bust that eliminates and ends the game, and rehydration through REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NinetyNineWebSocketControllerIT {

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
        CardDto dto = CardDto.from(NinetyNineTable.card(code));
        return Map.of("rank", dto.rank(), "suit", dto.suit(), "name", dto.name());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    /** A started two-player table, 2 lives each, the deals of rounds 1 and 2 scripted. */
    private final class Table {
        final String gameId;
        final String[] tokens = new String[2];
        final StompSession[] stomp = new StompSession[2];
        final Frames[] frames = new Frames[2];

        @SuppressWarnings("unchecked")
        Table() throws Exception {
            HttpResponse<String> created = post("/api/lobby", "{\"gameType\":\"ninety-nine\",\"name\":\"Alice\"}");
            assertThat(created.statusCode(), is(200));
            Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
            gameId = (String) room.get("gameId");
            tokens[0] = (String) room.get("token");
            sessionService.updateOptions(new GameId(gameId), tokens[0], Map.of("lives", "2"));
            HttpResponse<String> joined = post("/api/ninety-nine/game/" + gameId + "/join", "{\"name\":\"Bob\"}");
            assertThat(joined.statusCode(), is(200));
            tokens[1] = (String) objectMapper.readValue(joined.body(), Map.class).get("token");

            GameId id = new GameId(gameId);
            decks.fix(id, 1, cards("KS 9H 4S 7H 9S 8H 2S 3D 2C"));
            decks.fix(id, 2, cards("KD 9D 5D 8D 6D 7D 2D 3D"));
            decks.fixFirstSeat(id, 0);

            for (int seat = 0; seat < 2; seat++) {
                stomp[seat] = client.connect();
                frames[seat] = client.subscribeSeat(stomp[seat], gameId, tokens[seat]);
            }
            client.sendApp(stomp[0], "/ninety-nine/start", StompTestClient.body(gameId, tokens[0]));
            for (int seat = 0; seat < 2; seat++) {
                Map<String, Object> start = frames[seat].await(f -> "START".equals(f.get("eventType")));
                assertThat(state(start).get("phase"), is("PLAYING"));
            }
        }

        void play(int seat, String code, Integer aceValue) {
            client.sendApp(stomp[seat], "/ninety-nine/play", StompTestClient.body(gameId, tokens[seat],
                    "card", cardBody(code), "aceValue", aceValue));
        }

        void awaitBoth(String eventType) throws Exception {
            for (int s = 0; s < 2; s++) {
                frames[s].await(f -> eventType.equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
            }
        }

        Map<String, Object> rehydrate(int seat) throws Exception {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ninety-nine/game/" + gameId
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
        assertThat(((List<Object>) mine.get("hand")).size(), is(3));
        assertThat(mine.get("hand").equals(theirs.get("hand")), is(false));
        assertThat(mine.get("availableActions"), is(List.of("PLAY")));
        assertThat(theirs.get("availableActions"), is(List.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWholeGameRunsFromTheFirstPlayToTheEliminationAndTheEnd() throws Exception {
        Table table = new Table();

        table.play(0, "KS", null);
        table.awaitBoth("PLAY");
        table.play(1, "7H", null); // unsafe at 99 while the 9H is safe
        Map<String, Object> refused = table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")));
        assertThat(refused.get("eventType"), is("PLAY"));
        assertThat(table.refusals(1), is(1L));
        assertThat(table.refusals(0), is(0L));

        table.play(1, "9H", null);
        table.awaitBoth("PLAY");
        table.play(0, "9S", null);
        table.awaitBoth("PLAY");
        table.play(1, "7H", null); // seat 1 is stuck: it must play a card, which goes over 99
        for (int s = 0; s < 2; s++) { // then it busts, what it still holds is shown to both
            Map<String, Object> bust = table.frames[s].await(f -> "BUST".equals(f.get("eventType")));
            Map<String, Object> data = (Map<String, Object>) bust.get("eventData");
            assertThat(data.get("seat"), is(1));
            assertThat(data.get("livesLeft"), is(1));
            assertThat(((List<Object>) data.get("hand")).size(), is(2));
            Map<String, Object> lastBust = (Map<String, Object>) state(bust).get("lastBust");
            assertThat(lastBust.get("totalBefore"), is(106));
        }

        table.awaitBoth("ROUND_START"); // the pause is 0 under the test profile
        assertThat(table.rehydrate(0).get("round"), is(2));
        assertThat(table.rehydrate(0).get("currentSeat"), is(1));

        table.play(1, "9D", null);
        table.awaitBoth("PLAY");
        table.play(0, "KD", null);
        table.awaitBoth("PLAY");
        table.play(1, "8D", null);
        for (int s = 0; s < 2; s++) {
            Map<String, Object> eliminated = table.frames[s].await(f -> "ELIMINATED".equals(f.get("eventType")));
            assertThat(((Map<String, Object>) eliminated.get("eventData")).get("place"), is(2));
            Map<String, Object> over = table.frames[s].await(f -> "GAME_OVER".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(over).get("outcome");
            assertThat(outcome.get("status"), is("FINISHED"));
            assertThat(outcome.get("winners"), is(List.of(0)));
            assertThat(outcome.get("reason"), is("LAST_STANDING"));
        }
        assertThat(table.rehydrate(1).get("phase"), is("FINISHED"));
    }

    @Test
    void aSeatCannotPlayWithAForgedOrMissingToken() throws Exception {
        Table table = new Table();

        table.play(1, "9H", null); // out of turn: refused to its sender
        table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")));
        client.sendApp(table.stomp[1], "/ninety-nine/play", StompTestClient.body(table.gameId, "forged",
                "card", cardBody("9H"), "aceValue", null));
        client.sendApp(table.stomp[1], "/ninety-nine/play", StompTestClient.body(table.gameId, null,
                "card", cardBody("9H"), "aceValue", null));
        table.play(0, "KS", null);
        table.awaitBoth("PLAY");

        assertThat(table.rehydrate(0).get("version"), is(1));
        assertThat(table.refusals(1), is(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forfeitingThroughTheSharedForfeitEliminatesTheSeatAndEndsATwoSeatGame() throws Exception {
        Table table = new Table();

        client.sendApp(table.stomp[1], "/forfeit", StompTestClient.body(table.gameId, table.tokens[1]));

        for (int seat = 0; seat < 2; seat++) {
            Map<String, Object> forfeit = table.frames[seat].await(f -> "FORFEIT".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(forfeit).get("outcome");
            assertThat(outcome.get("winners"), is(List.of(0)));
            assertThat(outcome.get("reason"), is("FORFEIT"));
        }
    }
}
