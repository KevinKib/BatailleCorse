package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.cards;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.hands;

/**
 * A human at a table with two {@code easy} computer seats over the real wiring (profile {@code test}: bot delays are
 * 0). Every hand is a four of a kind, so the bots slap at once; the human, who never slaps, is the last one and takes
 * the first kilo card: the race ends by the bots' own slaps and the round reaches its end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KiloDeMerdeBotsIT {

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

    @Test
    @SuppressWarnings("unchecked")
    void aHumanPlaysAgainstTwoEasyBotsToTheEndOfARound() throws Exception {
        RoomCreated room = sessionService.createRoom("kilo-de-merde", "Alice");
        GameId id = new GameId(room.gameId());
        for (int bot = 1; bot <= 2; bot++) {
            sessionService.addBot(id, room.hostToken());
            sessionService.setSeatOption(id, room.hostToken(), bot, "level", "easy");
        }
        decks.fixDeal(id, hands(ALL_SETS), cards("AS 9C"));
        StompSession stomp = client.connect();
        Frames frames = client.subscribeSeat(stomp, room.gameId(), room.hostToken());

        client.sendApp(stomp, "/kilo-de-merde/start", StompTestClient.body(room.gameId(), room.hostToken()));
        frames.await(f -> "START".equals(f.get("eventType")));
        // the bots confirm on their own; the human is the last to say it is ready
        client.sendApp(stomp, "/kilo-de-merde/ready", StompTestClient.body(room.gameId(), room.hostToken()));

        Map<String, Object> over = frames.await(f -> "ROUND_OVER".equals(f.get("eventType")));
        Map<String, Object> state = (Map<String, Object>) over.get("state");
        Map<String, Object> outcome = (Map<String, Object>) state.get("lastOutcome");
        assertThat(outcome.get("kind"), is("RACE"));
        assertThat(((List<Object>) outcome.get("slapOrder")).size(), is(2));
        assertThat(((List<Map<String, Object>>) outcome.get("draws")).get(0).get("seat"), is(0));
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        assertThat(players.get(0).get("kilos"), is(100));
        assertThat(players.get(1).get("bot"), is(true));
        assertThat(players.get(1).get("level"), is("easy"));
    }
}
