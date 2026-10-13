package org.kevinkib.cardgames.kobo.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;
import static org.kevinkib.cardgames.presentation.support.StompTestClient.body;

/**
 * Kobo over a real WebSocket: lobby, start, private per-seat states, the matching-discard race between
 * two real connections, rejected commands and reconnection through REST. The deterministic games are
 * built with the domain builder and registered in the session repository (one token per seat).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KoboWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionRepository repository;

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

    private Kobo install(Kobo game) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), KoboFactory.GAME_TYPE);
        game.getPlayerIds().forEach(seat -> session.claimNextFreeSeat(null));
        repository.save(game, session);
        return game;
    }

    private String token(Kobo game, int seat) {
        return sessionService.tokenForSeat(game.getId(), new PlayerId(seat));
    }

    // ------------------------------------------------------------------ tests

    @Test
    @SuppressWarnings("unchecked")
    void lobbyStartAndPrivateStates() throws Exception {
        StompSession host = stomp.connect();
        HttpResponse<String> created = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/lobby"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"gameType\":\"kobo\",\"name\":\"Alice\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode(), is(200));
        Map<String, Object> eventData = objectMapper.readValue(created.body(), Map.class);
        String gameId = (String) eventData.get("gameId");
        String hostToken = (String) eventData.get("token");
        assertThat(sessionService.gameType(new GameId(gameId)), is("kobo"));

        String guestToken = sessionService.joinRoom(new GameId(gameId), "Bob").token();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, gameId, hostToken);
        Frames guestFrames = stomp.subscribeSeat(guest, gameId, guestToken);

        stomp.sendApp(host, "/kobo/start", body(gameId, hostToken));
        Map<String, Object> hostStart = hostFrames.await(f -> "START".equals(f.get("eventType")));
        Map<String, Object> guestStart = guestFrames.await(f -> "START".equals(f.get("eventType")));

        Kobo game = sessionService.getGame(new GameId(gameId), Kobo.class);
        List<Map<String, Object>> hostStarting = (List<Map<String, Object>>) state(hostStart).get("startingCards");
        List<Map<String, Object>> guestStarting = (List<Map<String, Object>>) state(guestStart).get("startingCards");
        assertThat(hostStarting, hasSize(2));
        assertThat(guestStarting, hasSize(2));
        String hostSecret = (String) ((Map<String, Object>) hostStarting.get(0).get("card")).get("name");
        String guestSecret = (String) ((Map<String, Object>) guestStarting.get(0).get("card")).get("name");
        assertThat(objectMapper.writeValueAsString(guestStart).contains(hostSecret), is(false));
        assertThat(objectMapper.writeValueAsString(hostStart).contains(guestSecret), is(false));

        stomp.sendApp(host, "/kobo/ready", body(gameId, hostToken));
        stomp.sendApp(guest, "/kobo/ready", body(gameId, guestToken));
        hostFrames.await(f -> "DRAW".equals(state(f).get("phase")));

        stomp.sendApp(host, "/kobo/draw", body(gameId, hostToken));
        Map<String, Object> hostDrew = hostFrames.await(f -> "DRAW".equals(f.get("eventType")));
        Map<String, Object> guestSaw = guestFrames.await(f -> "DRAW".equals(f.get("eventType")));
        assertThat(state(hostDrew).get("drawnCard"), notNullValue());
        assertThat(state(guestSaw).get("drawnCard"), nullValue());
        assertThat(state(guestSaw).get("hasDrawn"), is(true));
        assertThat(game.phase().name(), is("DECISION"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void twoConnectionsRacingForTheSameCard_exactlyOneWinsAndTheOtherIsNotPenalised() throws Exception {
        Kobo game = install(aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                .withTableau("AD", "2D", "3D", "4D").withDiscard("7H").withDrawPile("8C", "9C", "10C").build());
        String gameId = game.getId().uuid().toString();
        StompSession s0 = stomp.connect();
        StompSession s2 = stomp.connect();
        Frames f0 = stomp.subscribeSeat(s0, gameId, token(game, 0));
        Frames f2 = stomp.subscribeSeat(s2, gameId, token(game, 2));
        String seven = card("7H").getRank().toString();

        stomp.sendApp(s0, "/kobo/match", body(gameId, token(game, 0), "seat", 1, "slot", 0, "giveSlot", 0,
                "expectedTopRank", seven, "expectedRevision", 0));
        stomp.sendApp(s2, "/kobo/match", body(gameId, token(game, 2), "seat", 1, "slot", 0, "giveSlot", 0,
                "expectedTopRank", seven, "expectedRevision", 0));

        // the winner's broadcast reaches both seats; which seat won is decided by the server
        f0.await(f -> "MATCH".equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
        f2.await(f -> "MATCH".equals(f.get("eventType")) && Boolean.TRUE.equals(f.get("success")));
        // one of the two seats got the rejection, the other gave a card away
        boolean zeroWon = game.handSize(new PlayerId(0)) == 3;
        boolean twoWon = game.handSize(new PlayerId(2)) == 3;
        assertThat(zeroWon ^ twoWon, is(true));
        Frames loser = zeroWon ? f2 : f0;
        loser.awaitAny(f -> Boolean.FALSE.equals(f.get("success")));
        for (int seat = 0; seat < 3; seat++) {
            assertThat(game.slots(new PlayerId(seat)).size(), is(4));
        }
        assertThat(game.cardsInPlay(), is(4 + 4 + 4 + 3 + 1));
        assertThat(game.drawPileSize(), is(3));
    }

    @Test
    void aRejectedCommandAnswersTheSenderOnly_andRehydrationMatchesTheLastState() throws Exception {
        Kobo game = install(aKobo().withPlayers(2).build());
        String gameId = game.getId().uuid().toString();
        StompSession s0 = stomp.connect();
        StompSession s1 = stomp.connect();
        Frames f0 = stomp.subscribeSeat(s0, gameId, token(game, 0));
        Frames f1 = stomp.subscribeSeat(s1, gameId, token(game, 1));

        stomp.sendApp(s1, "/kobo/draw", body(gameId, token(game, 1)));
        Map<String, Object> error = f1.await(f -> Boolean.FALSE.equals(f.get("success")));
        assertThat(error.get("eventType"), is("DRAW"));

        stomp.sendApp(s0, "/kobo/draw", body(gameId, token(game, 0)));
        Map<String, Object> first = f0.await(f -> true);
        assertThat(first.get("success"), is(true));
        assertThat(first.get("eventType"), is("DRAW"));

        Map<String, Object> rest = rehydrate(gameId, token(game, 0));
        assertThat(rest.get("version"), is(state(first).get("version")));
        assertThat(rest.get("phase"), is("DECISION"));
        assertThat(rest.get("drawnCard"), notNullValue());
        assertThat(rehydrate(gameId, token(game, 1)).get("drawnCard"), nullValue());
    }

    @Test
    void aSeatCannotSubscribeToAnotherSeatChannel() throws Exception {
        Kobo game = install(aKobo().withPlayers(2).build());
        String gameId = game.getId().uuid().toString();
        StompSession s0 = stomp.connect();
        String stolen = "/topic/game/" + gameId + "/seat/" + token(game, 1);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(stolen);
        headers.add("token", token(game, 0));
        s0.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) { }
        });
        stomp.subscribeSeat(s0, gameId, token(game, 0)); // a legitimate subscription registered after it

        assertThat(stomp.hasBrokerSubscription(stolen), is(false));
    }

    private Map<String, Object> rehydrate(String gameId, String token) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/kobo/game/" + gameId
                        + "?token=" + token)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode(), is(200));
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
        return parsed;
    }
}
