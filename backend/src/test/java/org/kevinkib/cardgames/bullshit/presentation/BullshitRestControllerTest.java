package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BullshitRestControllerTest {

    private SessionService sessionService;
    private BullshitRestController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        controller = new BullshitRestController(sessionService);
    }

    @Test
    void givenValidToken_whenGetGame_thenReturnsOwnView() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
        GameId id = game.getId();
        SessionToken t0 = sessionService.loadTokenByPlayerId(id, new PlayerId(0));

        ResponseEntity<BullshitDto> response = controller.getGame(id.uuid().toString(), t0.uuid().toString());

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody().myHand().isEmpty(), is(false));
    }

    @Test
    void givenNoToken_whenGetGame_thenForbidden() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);

        ResponseEntity<BullshitDto> response = controller.getGame(game.getId().uuid().toString(), null);

        assertThat(response.getStatusCode().value(), is(403));
    }

    @Test
    void givenUnknownGame_whenGetGame_thenNotFound() {
        ResponseEntity<BullshitDto> response = controller.getGame(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value(), is(404));
    }
}
