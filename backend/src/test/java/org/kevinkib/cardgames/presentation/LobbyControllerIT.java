package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Lobby commands over a real WebSocket: rename, bots and seat options reach every seat live. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyControllerIT {

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
    private static List<Map<String, Object>> players(Map<String, Object> frame) {
        Map<String, Object> state = (Map<String, Object>) frame.get("state");
        return state == null ? List.of() : (List<Map<String, Object>>) state.get("players");
    }

    @Test
    void guestRenames_hostSeesTheNewNameLive() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        stomp.subscribeSeat(guest, room.gameId(), guestToken);

        stomp.send(guest, "/app/lobby/rename", Map.of("gameId", room.gameId(), "token", guestToken, "name", "Robert"));

        hostFrames.await(f -> players(f).size() > 1 && "Robert".equals(players(f).get(1).get("name")));
    }

    @Test
    void hostAddsAndRemovesABot_onABullshitLobby() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        StompSession host = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());

        stomp.send(host, "/app/lobby/addBot", Map.of("gameId", room.gameId(), "token", room.hostToken()));
        hostFrames.await(f -> players(f).size() > 1 && Boolean.TRUE.equals(players(f).get(1).get("bot")));

        stomp.send(host, "/app/lobby/removeBot", Map.of("gameId", room.gameId(), "token", room.hostToken(), "seat", 1));
        hostFrames.await(f -> players(f).size() > 1 && Boolean.FALSE.equals(players(f).get(1).get("joined")));
    }

    @Test
    void hostAddsABot_onABatailleCorseLobby() throws Exception {
        RoomCreated room = sessionService.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");
        StompSession host = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());

        stomp.send(host, "/app/lobby/addBot", Map.of("gameId", room.gameId(), "token", room.hostToken()));

        hostFrames.await(f -> players(f).size() > 1 && Boolean.TRUE.equals(players(f).get(1).get("bot")));
    }

    @Test
    void invalidSeatOption_isRefusedToTheHost() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        sessionService.addBot(new GameId(room.gameId()), room.hostToken());
        StompSession host = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());

        stomp.send(host, "/app/lobby/setSeatOption",
                Map.of("gameId", room.gameId(), "token", room.hostToken(), "seat", 1, "key", "tier", "value", "3"));

        hostFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
    }
}
