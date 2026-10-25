package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.presentation.dto.PublicLobbyListDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobby;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyList;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyState;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** The REST snapshot, against a {@link SessionService} whose {@code publicLobbies} is faked (the rules are B1's). */
class PublicLobbyRestControllerTest {

    private static final class FakeSessionService extends SessionService {
        FakeSessionService() {
            super(new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory(), new KoboFactory())));
        }

        @Override
        public synchronized PublicLobbyList publicLobbies(String gameType) {
            if (!List.of("kobo", "bullshit").contains(gameType)) {
                throw new IllegalArgumentException("Unknown game type: " + gameType);
            }
            return new PublicLobbyList(gameType,
                    List.of(new PublicLobby("g1", gameType, "Alice", 2, 1, 4, PublicLobbyState.OPEN, Instant.now())),
                    1, false, 7L);
        }
    }

    private final PublicLobbyRestController controller = new PublicLobbyRestController(new FakeSessionService());

    @Test
    void list_answers200WithTheListOfTheGameType() {
        ResponseEntity<PublicLobbyListDto> response = controller.list("kobo");

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        PublicLobbyListDto body = response.getBody();
        assertThat(body.gameType(), is("kobo"));
        assertThat(body.version(), is(7L));
        assertThat(body.lobbies().size(), is(1));
        assertThat(body.lobbies().get(0).hostName(), is("Alice"));
        assertThat(body.lobbies().get(0).bots(), is(1));
    }

    @Test
    void list_answers404ForAnUnknownGameType() {
        assertThat(controller.list("chess").getStatusCode(), is(HttpStatus.NOT_FOUND));
    }
}
