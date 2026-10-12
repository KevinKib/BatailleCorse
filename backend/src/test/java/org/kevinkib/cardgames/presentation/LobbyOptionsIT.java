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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** The host edits the game options of a lobby over a real WebSocket; the guest sees the change live. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyOptionsIT {

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

    private void setOptions(StompSession session, String gameId, String token, Map<String, String> values) {
        stomp.send(session, "/app/lobby/setOptions", Map.of("gameId", gameId, "token", token, "values", values));
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

    @Test
    void hostEditsOptions_guestSeesTheChangeLive() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = stomp.subscribeSeat(guest, room.gameId(), guestToken);

        setOptions(host, room.gameId(), room.hostToken(), Map.of("claimMode", "suit"));

        Map<String, Object> hostSees = hostFrames.await(f -> "suit".equals(options(f).get("claimMode")));
        Map<String, Object> guestSees = guestFrames.await(f -> "suit".equals(options(f).get("claimMode")));
        assertThat(state(hostSees).get("canEditOptions"), is(true));
        assertThat(state(guestSees).get("canEditOptions"), is(false));
        assertThat(((List<?>) state(guestSees).get("optionDescriptors")).size(), is(2));
    }

    @Test
    void guestEditingOptions_isRefusedToTheGuestOnly() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = stomp.subscribeSeat(guest, room.gameId(), guestToken);

        setOptions(guest, room.gameId(), guestToken, Map.of("claimMode", "suit"));

        guestFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
        // a later accepted change is the next frame the host receives: nothing was broadcast before it
        setOptions(host, room.gameId(), room.hostToken(), Map.of("deckSize", "52"));
        Map<String, Object> hostSees = hostFrames.await(f -> true);
        assertThat(options(hostSees).get("claimMode"), is("rank"));
        assertThat(options(hostSees).get("deckSize"), is("52"));
    }

    @Test
    void invalidValue_isRefusedToTheHostAndNothingChanges() throws Exception {
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        String guestToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        StompSession host = stomp.connect();
        StompSession guest = stomp.connect();
        Frames hostFrames = stomp.subscribeSeat(host, room.gameId(), room.hostToken());
        Frames guestFrames = stomp.subscribeSeat(guest, room.gameId(), guestToken);

        setOptions(host, room.gameId(), room.hostToken(), Map.of("claimMode", "bogus"));

        hostFrames.await(f -> Boolean.FALSE.equals(f.get("success")));
        setOptions(host, room.gameId(), room.hostToken(), Map.of("claimMode", "suit"));
        Map<String, Object> guestSees = guestFrames.await(f -> true);
        assertThat(options(guestSees).get("claimMode"), is("suit"));
    }
}
