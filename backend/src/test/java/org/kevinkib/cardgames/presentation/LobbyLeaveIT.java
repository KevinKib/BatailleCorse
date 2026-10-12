package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Leaving a lobby over a real WebSocket: the seats shift and every remaining client follows on its token channel. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyLeaveIT {

    private static final int TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry connectionRegistry;

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @Test
    void guestLeaves_hostSeesTheFreeSeat() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        stomp.subscribeSeat(guest, room.gameId(), guestToken);

        stomp.send(guest, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", guestToken));

        hostFrames.await(f -> players(f).size() > 1 && Boolean.FALSE.equals(players(f).get(1).get("joined")));
    }

    @Test
    void hostLeaves_theNextClientBecomesHostOnTheSameSubscription() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        String carolToken = sessionService.joinRoom(new GameId(room.gameId()), "Carol").token();
        StompSession host = stomp.connect();
        StompSession bob = stomp.connect();
        StompSession carol = stomp.connect();
        stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames bobFrames = stomp.subscribeSeat(bob, room.gameId(), bobToken);
        Frames carolFrames = stomp.subscribeSeat(carol, room.gameId(), carolToken);

        stomp.send(host, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", room.hostToken()));

        Map<String, Object> bobView = state(bobFrames.await(f -> state(f) != null
                && Integer.valueOf(0).equals(state(f).get("mySeat"))));
        assertThat(bobView.get("canStart"), is(true));
        assertThat(bobView.get("hostSeat"), is(0));
        Map<String, Object> carolView = state(carolFrames.await(f -> state(f) != null
                && Integer.valueOf(1).equals(state(f).get("mySeat"))));
        assertThat(carolView.get("canStart"), is(false));
    }

    @Test
    void leaversOldSubscription_receivesNothingAfterANewcomerTakesTheSeat() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        String bobToken = sessionService.joinRoom(id, "Bob").token();
        StompSession host = stomp.connect();
        StompSession bob = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames bobFrames = stomp.subscribeSeat(bob, room.gameId(), bobToken);
        stomp.send(bob, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", bobToken));
        hostFrames.await(f -> players(f).size() > 1 && Boolean.FALSE.equals(players(f).get(1).get("joined")));

        String carolToken = sessionService.joinRoom(id, "Carol").token();
        StompSession carol = stomp.connect();
        Frames carolFrames = stomp.subscribeSeat(carol, room.gameId(), carolToken);
        stomp.send(host, "/app/lobby/rename", Map.of("gameId", room.gameId(), "token", room.hostToken(), "name", "Alicia"));
        carolFrames.await(f -> players(f).size() > 1 && "Alicia".equals(players(f).get(0).get("name")));
        hostFrames.await(f -> players(f).size() > 1 && "Alicia".equals(players(f).get(0).get("name")));

        assertThat(bobFrames.drain().isEmpty(), is(true));
    }

    @Test
    void lastHumanLeaves_theLobbyIsRemovedThroughTheEvictionListeners() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessionService.addBot(id, room.hostToken());
        connectionRegistry.bind("conn-leave-it", new org.kevinkib.cardgames.sessionmanagement.presence.domain.SeatClaim(
                id, room.hostToken()));
        StompSession host = stomp.connect();
        stomp.subscribeSeat(host, room.gameId(), room.hostToken());

        stomp.send(host, "/app/lobby/leave", Map.of("gameId", room.gameId(), "token", room.hostToken()));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (connectionRegistry.claimOf("conn-leave-it").isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(connectionRegistry.claimOf("conn-leave-it").isPresent(), is(false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sessionService.joinRoom(id, "Bob"));
    }
}
