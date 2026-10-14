package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.presentation.support.StompTestClient;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * The frames of one client are handled, and their answers delivered, in the order they were sent. Without
 * {@code setPreserveReceiveOrder} / {@code setPreservePublishOrder} a burst of renames from one session is
 * applied and broadcast out of order (see docs/architecture/e2e-instability-investigation.md, section 2.3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompMessageOrderIT {

    private static final int BURST = 200;

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
    private static String hostName(Map<String, Object> frame) {
        Map<String, Object> state = (Map<String, Object>) frame.get("state");
        if (state == null) {
            return "";
        }
        return (String) ((List<Map<String, Object>>) state.get("players")).get(0).get("name");
    }

    @Test
    void aBurstOfRenames_isAppliedAndBroadcastInSendingOrder() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        StompSession host = stomp.connect();
        Frames frames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());

        for (int i = 1; i <= BURST; i++) {
            stomp.sendApp(host, "/lobby/rename", StompTestClient.body(room.gameId(), room.hostToken(), "name", "N" + i));
        }

        // the last name sent is the one the server keeps, so it is the one it ends up broadcasting
        frames.awaitAny(f -> ("N" + BURST).equals(hostName(f)));
        List<Integer> broadcastOrder = new ArrayList<>();
        for (Map<String, Object> frame : frames.all) {
            String name = hostName(frame);
            if (name.startsWith("N")) {
                broadcastOrder.add(Integer.parseInt(name.substring(1)));
            }
        }
        List<Integer> sorted = new ArrayList<>(broadcastOrder);
        sorted.sort(null);
        assertThat("renames broadcast in sending order", broadcastOrder, is(sorted));
        assertThat("last rename wins", broadcastOrder.get(broadcastOrder.size() - 1), is(BURST));
    }
}
