package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.LobbyPrivatePayload;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.AddressedLobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.application.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * The {@code /lobby/setPrivate} command, against a {@link SessionService} whose {@code setPrivate} is faked: the
 * real rule (host only, lobby only) is B1's, this layer only has to route the command and its answers.
 */
class LobbyControllerSetPrivateTest {

    /** Records the call and answers like a successful use case would: the lobby of every human seat. */
    private static final class FakeSessionService extends SessionService {
        final List<String> calls = new ArrayList<>();
        RuntimeException refusal;

        FakeSessionService() {
            super(new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory())));
        }

        @Override
        public synchronized List<AddressedLobbyView> setPrivate(GameId id, String hostToken, boolean privateGame) {
            calls.add(hostToken + ":" + privateGame);
            if (refusal != null) {
                throw refusal;
            }
            return updateOptions(id, hostToken, Map.of());
        }
    }

    private FakeSessionService sessionService;
    private RecordingMessaging messaging;
    private LobbyController controller;

    @BeforeEach
    void setUp() {
        sessionService = new FakeSessionService();
        messaging = new RecordingMessaging().resolvingTokensWith(sessionService);
        PresenceService presence = new PresenceService(sessionService, new InMemoryConnectionRegistry(),
                (deadline, task) -> () -> { }, Clock.systemUTC(), new InMemoryForfeitLog(),
                new GameLifecycleBroadcasters(List.of()));
        controller = new LobbyController(sessionService, messaging, presence);
    }

    @Test
    void givenHostWithAGuest_whenSetPrivate_thenTheUseCaseIsCalledAndEveryHumanSeatReceivesALobby() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        messaging.clear();

        controller.setPrivate(new LobbyPrivatePayload(room.gameId(), room.hostToken(), true));

        assertThat(sessionService.calls, contains(room.hostToken() + ":true"));
        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(true));
    }

    @Test
    void givenARefusal_whenSetPrivate_thenOnlyTheSenderIsTold() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();
        sessionService.refusal = new IllegalStateException("Only the host can change the visibility.");

        controller.setPrivate(new LobbyPrivatePayload(room.gameId(), bobToken, true));

        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenAnUnknownToken_whenSetPrivate_thenNothingHappens() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        messaging.clear();

        controller.setPrivate(new LobbyPrivatePayload(room.gameId(), "nope", true));

        assertThat(sessionService.calls.isEmpty(), is(true));
        assertThat(messaging.payloads.isEmpty(), is(true));
    }
}
