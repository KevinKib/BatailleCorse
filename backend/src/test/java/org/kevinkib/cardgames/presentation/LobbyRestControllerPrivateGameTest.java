package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.presentation.api.CreateLobbyPayload;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/** {@code POST /api/lobby} hands the {@code privateGame} flag to the session core (absent means public). */
class LobbyRestControllerPrivateGameTest {

    private static final class RecordingSessionService extends SessionService {
        final List<Boolean> privateFlags = new ArrayList<>();

        RecordingSessionService() {
            super(new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory())));
        }

        @Override
        public synchronized RoomCreated createRoom(String gameType, String hostName, GameOptions options,
                                                   boolean privateGame) {
            privateFlags.add(privateGame);
            return super.createRoom(gameType, hostName, options, privateGame);
        }
    }

    private final RecordingSessionService sessions = new RecordingSessionService();
    private final LobbyRestController controller = new LobbyRestController(sessions);

    @Test
    void create_passesPrivateGameTrueToTheSessionCore() {
        controller.create(new CreateLobbyPayload("bullshit", "Alice", true));

        assertThat(sessions.privateFlags, contains(true));
    }

    @Test
    void create_treatsAMissingOrNullPrivateGameAsPublic() {
        controller.create(new CreateLobbyPayload("bullshit", "Alice"));
        controller.create(new CreateLobbyPayload("bullshit", "Alice", null));
        controller.create(new CreateLobbyPayload("bullshit", "Alice", false));

        assertThat(sessions.privateFlags, contains(false, false, false));
    }
}
