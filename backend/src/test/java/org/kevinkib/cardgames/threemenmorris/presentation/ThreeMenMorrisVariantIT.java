package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
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

/** A room created or edited with the {@code variant} option starts the matching game over a real WebSocket. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThreeMenMorrisVariantIT {

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
    private static Map<String, Object> state(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("state");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> options(Map<String, Object> frame) {
        Map<String, Object> state = state(frame);
        return state == null ? Map.of() : (Map<String, String>) state.get("options");
    }

    /** Starts the room and returns the START frame seen by the host. */
    private Map<String, Object> startAndAwaitStart(RoomCreated room, String guestToken) throws Exception {
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        stomp.subscribeSeat(guest, room.gameId(), guestToken);
        stomp.sendApp(host, "/three-men-morris/start", StompTestClient.body(room.gameId(), room.hostToken()));
        return hostFrames.await(f -> "START".equals(f.get("eventType")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> players(Map<String, Object> frame) {
        return (List<Map<String, Object>>) state(frame).get("players");
    }

    @Test
    void aRoomCreatedWithTheAchiVariantStartsWithFourPiecesInHand() throws Exception {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of("variant", "achi", "hostColour", "white")));
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();

        Map<String, Object> start = startAndAwaitStart(room, guestToken);

        assertThat(state(start).get("variant"), is("achi"));
        assertThat(players(start).get(0).get("piecesToPlace"), is(4));
        assertThat(players(start).get(1).get("piecesToPlace"), is(4));
        assertThat(state(start).get("legalPlacements"), is(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8)));
    }

    @Test
    void aRoomCreatedWithTheSixVariantStartsWithSixteenPointsAndSixPiecesInHand() throws Exception {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of("variant", "six", "hostColour", "white")));
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();

        Map<String, Object> start = startAndAwaitStart(room, guestToken);

        assertThat(state(start).get("variant"), is("six"));
        assertThat(((List<?>) state(start).get("board")).size(), is(16));
        assertThat(players(start).get(0).get("piecesToPlace"), is(6));
        assertThat(players(start).get(0).get("lost"), is(0));
        assertThat(state(start).get("quietPlyLimit"), is(50));
        assertThat(((List<?>) state(start).get("legalPlacements")).size(), is(16));
    }

    @Test
    void theHostPicksSixInTheLobbyAndAMillIsFollowedByARemovalBetweenTwoSeats() throws Exception {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = stomp.subscribeSeat(guest, room.gameId(), guestToken);
        stomp.send(host, "/app/lobby/setOptions", Map.of("gameId", room.gameId(), "token", room.hostToken(),
                "values", Map.of("variant", "six", "hostColour", "white")));
        guestFrames.await(f -> "six".equals(options(f).get("variant")));
        stomp.sendApp(host, "/three-men-morris/start", StompTestClient.body(room.gameId(), room.hostToken()));
        hostFrames.await(f -> "START".equals(f.get("eventType")));
        guestFrames.await(f -> "START".equals(f.get("eventType")));

        int[][] placements = {{0, 0}, {1, 8}, {0, 1}, {1, 10}, {0, 2}};
        String[] tokens = {room.hostToken(), guestToken};
        Frames[] frames = {hostFrames, guestFrames};
        StompSession[] sessions = {host, guest};
        Map<String, Object> pending = null;
        for (int i = 0; i < placements.length; i++) {
            int[] placement = placements[i];
            int expectedVersion = i + 1;
            stomp.sendApp(sessions[placement[0]], "/three-men-morris/place",
                    StompTestClient.body(room.gameId(), tokens[placement[0]], "point", placement[1]));
            for (Frames f : frames) {
                Map<String, Object> seen = f.await(g -> "PLACE".equals(g.get("eventType"))
                        && Boolean.TRUE.equals(g.get("success"))
                        && ((Number) state(g).get("version")).intValue() == expectedVersion);
                if (f == hostFrames) {
                    pending = seen;
                }
            }
        }

        assertThat(state(pending).get("removal") != null, is(true));
        assertThat(state(pending).get("availableActions"), is(List.of("REMOVE")));
        assertThat(state(pending).get("legalRemovals"), is(List.of(8, 10)));

        stomp.sendApp(guest, "/three-men-morris/remove",
                StompTestClient.body(room.gameId(), guestToken, "point", 8));
        guestFrames.await(f -> Boolean.FALSE.equals(f.get("success")));

        stomp.sendApp(host, "/three-men-morris/remove", StompTestClient.body(room.gameId(), room.hostToken(), "point", 8));
        Map<String, Object> removed = guestFrames.await(f -> "REMOVE".equals(f.get("eventType")));
        assertThat(state(removed).get("currentSeat"), is(1));
        assertThat(state(removed).containsKey("removal"), is(false));
        assertThat(players(removed).get(1).get("lost"), is(1));
        assertThat(((List<?>) state(removed).get("board")).get(8), is((Object) null));
    }

    @Test
    void anOldTabPickingPicariaIsRefusedAndTheLobbyKeepsItsValue() throws Exception {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = stomp.subscribeSeat(guest, room.gameId(), guestToken);

        stomp.send(host, "/app/lobby/setOptions",
                Map.of("gameId", room.gameId(), "token", room.hostToken(), "values", Map.of("variant", "picaria")));

        hostFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
        stomp.send(host, "/app/lobby/setOptions",
                Map.of("gameId", room.gameId(), "token", room.hostToken(), "values", Map.of("variant", "achi")));
        Map<String, Object> guestSees = guestFrames.await(f -> true);
        assertThat(options(guestSees).get("variant"), is("achi"));
    }

    @Test
    void aRoomCreatedWithHostColourBlackStartsWithSeatOneToPlay() throws Exception {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of("hostColour", "black")));
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();

        Map<String, Object> start = startAndAwaitStart(room, guestToken);

        assertThat(state(start).get("currentSeat"), is(1));
        assertThat(state(start).get("whiteSeat"), is(1));
        assertThat(players(start).get(0).get("colour"), is("BLACK"));
        assertThat(players(start).get(1).get("colour"), is("WHITE"));
        assertThat(players(start).get(1).get("current"), is(true));
    }

    @Test
    void aRoomWithoutAVariantStartsAsTapatanWithoutTheNewFields() throws Exception {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice",
                GameOptions.of(Map.of("hostColour", "white")));
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();

        Map<String, Object> start = startAndAwaitStart(room, guestToken);

        assertThat(state(start).containsKey("variant"), is(false));
        assertThat(state(start).containsKey("legalPlacements"), is(false));
        assertThat(players(start).get(0).get("piecesToPlace"), is(3));
    }
}
