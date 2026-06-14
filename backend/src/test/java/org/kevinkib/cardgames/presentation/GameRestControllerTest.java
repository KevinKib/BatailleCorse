package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.PlayerDto;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GameRestControllerTest {

    /** Records every broadcast instead of touching a real broker. */
    private static final class RecordingMessaging extends GameMessagingService {
        final List<Response> sent = new ArrayList<>();
        RecordingMessaging() { super(null); }
        @Override public void sendToGame(String gameId, Object payload) { sent.add((Response) payload); }
    }

    private SessionService sessionService;
    private ForfeitReasonRegistry forfeitReasonRegistry;
    private GameRestController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-11T10:00:00Z"), ZoneOffset.UTC);
        sessionService = new SessionService(new InMemorySessionRepository(clock));
        forfeitReasonRegistry = new ForfeitReasonRegistry();
        controller = new GameRestController(sessionService, new RecordingMessaging(), forfeitReasonRegistry);
    }

    @Test
    void givenForfeitedGame_whenGetGame_thenLosingPlayerHasForfeitReason() {
        BatailleCorse game = sessionService.createGame(2, GameMode.MULTIPLAYER);
        GameId gameId = game.getId();
        game.concede(new PlayerId(0));
        forfeitReasonRegistry.record(new Seat(gameId, new PlayerId(0)), ForfeitReason.RESIGNED);

        ResponseEntity<BatailleCorseDto> response = controller.getGame(gameId.uuid().toString());

        BatailleCorseDto body = response.getBody();
        PlayerDto loser = body.getPlayers().stream()
                .filter(p -> p.getId().equals("0"))
                .findFirst()
                .orElseThrow();
        assertThat(loser.getForfeitReason(), is("RESIGNED"));
    }
}
