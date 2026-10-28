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
 * Blackjack over a real WebSocket with two clients (profile {@code test}: the shoe is scripted): lobby creation, join,
 * start, sealed bets, an out-of-turn action refused to its sender alone, a bust, the dealer's play and settlement, an
 * elimination that ends the game, and rehydration through REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BlackjackWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ScriptedShoeSource shoes;

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    /**
     * A started two-player table, maxBet 100. Hand 1: seat 0 holds 10S 7C, seat 1 9H 8C, the dealer 6D over 5H;
     * the next cards are 10D (seat 0 busts on a hit) and 10C (the dealer's draw: 21).
     */
    private final class Table {
        final String gameId;
        final String[] tokens = new String[2];
        final StompSession[] stomp = new StompSession[2];
        final Frames[] frames = new Frames[2];

        @SuppressWarnings("unchecked")
        Table() throws Exception {
            HttpResponse<String> created = post("/api/lobby", "{\"gameType\":\"blackjack\",\"name\":\"Alice\"}");
            assertThat(created.statusCode(), is(200));
            Map<String, Object> room = objectMapper.readValue(created.body(), Map.class);
            gameId = (String) room.get("gameId");
            tokens[0] = (String) room.get("token");
            sessionService.updateOptions(new GameId(gameId), tokens[0], Map.of("maxBet", "100"));
            HttpResponse<String> joined = post("/api/blackjack/game/" + gameId + "/join", "{\"name\":\"Bob\"}");
            assertThat(joined.statusCode(), is(200));
            tokens[1] = (String) objectMapper.readValue(joined.body(), Map.class).get("token");

            shoes.fix(new GameId(gameId), 1, cards("10S 9H 6D 7C 8C 5H 10D 10C"));

            for (int seat = 0; seat < 2; seat++) {
                stomp[seat] = client.connect();
                frames[seat] = client.subscribeSeat(stomp[seat], gameId, tokens[seat]);
            }
            client.sendApp(stomp[0], "/blackjack/start", StompTestClient.body(gameId, tokens[0]));
            for (int seat = 0; seat < 2; seat++) {
                Map<String, Object> start = frames[seat].await(f -> "START".equals(f.get("eventType")));
                assertThat(state(start).get("phase"), is("BETTING"));
            }
        }

        void bet(int seat, int amount) {
            client.sendApp(stomp[seat], "/blackjack/bet", StompTestClient.body(gameId, tokens[seat], "amount", amount));
        }

        void act(int seat, String command) {
            client.sendApp(stomp[seat], "/blackjack/" + command, StompTestClient.body(gameId, tokens[seat]));
        }

        void awaitBoth(String eventType) throws Exception {
            for (int s = 0; s < 2; s++) {
                frames[s].await(f -> eventType.equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
            }
        }

        Map<String, Object> rehydrate(int seat) throws Exception {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/blackjack/game/" + gameId
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
    void theStartOffersTheBetToEverySeatAndSealsTheOthersBets() throws Exception {
        Table table = new Table();

        Map<String, Object> mine = state(table.frames[0].all.stream()
                .filter(f -> "START".equals(f.get("eventType"))).findFirst().orElseThrow());
        assertThat(mine.get("availableActions"), is(List.of("BET")));
        assertThat(((Map<String, Object>) mine.get("betLimits")).get("max"), is(100));

        table.bet(1, 20);
        table.awaitBoth("BET");
        Map<String, Object> seenByAlice = table.frames[0].all.get(table.frames[0].all.size() - 1);
        List<Map<String, Object>> players = (List<Map<String, Object>>) state(seenByAlice).get("players");
        assertThat(players.get(1).get("betPlaced"), is(true));
        assertThat(players.get(1).get("bet"), is((Object) null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWholeGameRunsFromTheBetsToTheEliminationAndTheEnd() throws Exception {
        Table table = new Table();

        table.bet(0, 100);
        table.bet(1, 10);
        table.awaitBoth("DEAL");
        Map<String, Object> rehydrated = table.rehydrate(0);
        assertThat(rehydrated.get("phase"), is("PLAYING"));
        assertThat(rehydrated.get("currentSeat"), is(0));
        Map<String, Object> dealer = (Map<String, Object>) rehydrated.get("dealer");
        assertThat(dealer.get("holeHidden"), is(true));
        assertThat(((List<Object>) dealer.get("cards")).size(), is(1));

        table.act(1, "stand"); // out of turn: refused to its sender alone
        table.frames[1].await(f -> Boolean.FALSE.equals(f.get("success")));
        Map<String, Object> refused = table.frames[1].all.stream()
                .filter(f -> Boolean.FALSE.equals(f.get("success"))).findFirst().orElseThrow();
        assertThat(refused.get("errorCode"), is("NOT_YOUR_TURN"));
        assertThat(table.refusals(1), is(1L));
        assertThat(table.refusals(0), is(0L));

        table.act(0, "hit"); // 17 + 10 = 27
        table.awaitBoth("BUST");
        table.act(1, "stand");
        for (int s = 0; s < 2; s++) {
            Map<String, Object> played = table.frames[s].await(f -> "DEALER_PLAY".equals(f.get("eventType")));
            Map<String, Object> data = (Map<String, Object>) played.get("eventData");
            assertThat(data.get("value"), is(21));
            assertThat(((List<Object>) data.get("cards")).size(), is(1));
            Map<String, Object> eliminated = table.frames[s].await(f -> "ELIMINATED".equals(f.get("eventType")));
            assertThat(((Map<String, Object>) eliminated.get("eventData")).get("seat"), is(0));
            Map<String, Object> over = table.frames[s].await(f -> "GAME_OVER".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(over).get("outcome");
            assertThat(outcome.get("status"), is("FINISHED"));
            assertThat(outcome.get("reason"), is("LAST_STANDING"));
            assertThat(((Map<String, Object>) outcome.get("places")).get("1"), is(1));
            assertThat(((Map<String, Object>) outcome.get("places")).get("0"), is(2));
        }
        assertThat(table.rehydrate(1).get("phase"), is("FINISHED"));
    }

    @Test
    void aSeatCannotBetWithAForgedOrMissingToken() throws Exception {
        Table table = new Table();

        client.sendApp(table.stomp[1], "/blackjack/bet", StompTestClient.body(table.gameId, "forged", "amount", 10));
        client.sendApp(table.stomp[1], "/blackjack/bet", StompTestClient.body(table.gameId, null, "amount", 10));
        table.bet(0, 10);
        table.awaitBoth("BET");

        assertThat(table.rehydrate(0).get("version"), is(1));
        assertThat(table.refusals(1), is(0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forfeitingThroughTheSharedForfeitEndsATwoSeatGame() throws Exception {
        Table table = new Table();

        client.sendApp(table.stomp[1], "/forfeit", StompTestClient.body(table.gameId, table.tokens[1]));

        for (int seat = 0; seat < 2; seat++) {
            Map<String, Object> forfeit = table.frames[seat].await(f -> "FORFEIT".equals(f.get("eventType")));
            Map<String, Object> outcome = (Map<String, Object>) state(forfeit).get("outcome");
            assertThat(outcome.get("reason"), is("FORFEIT"));
            Map<String, Object> over = table.frames[seat].await(f -> "GAME_OVER".equals(f.get("eventType")));
            assertThat(((Map<String, Object>) state(over).get("outcome")).get("reason"), is("FORFEIT"));
        }
    }
}
