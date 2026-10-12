package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerDto;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BatailleCorseRestControllerTest {


    private SessionService sessionService;
    private ForfeitLog forfeitLog;
    private BatailleCorseRestController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-11T10:00:00Z"), ZoneOffset.UTC);
        sessionService = new SessionService(new InMemorySessionRepository(clock), new org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories(java.util.List.of(new org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory())));
        forfeitLog = new InMemoryForfeitLog();
        controller = new BatailleCorseRestController(sessionService, forfeitLog, new LobbyBroadcaster(new RecordingMessaging()));
    }

    @Test
    void givenForfeitedGame_whenGetGame_thenLosingPlayerHasForfeitReason() {
        RoomCreated room = sessionService.createRoom("bataille-corse", "Alice");
        GameId gameId = new GameId(room.gameId());
        sessionService.addBot(gameId, room.hostToken());
        BatailleCorse game = sessionService.startGame(gameId, room.hostToken(), BatailleCorse.class);
        game.forfeit(new PlayerId(0));
        forfeitLog.record(new Seat(gameId, new PlayerId(0)), ForfeitReason.RESIGNED);

        ResponseEntity<?> response = controller.getGame(gameId.uuid().toString(), room.hostToken());

        BatailleCorseDto body = (BatailleCorseDto) response.getBody();
        PlayerDto loser = body.getPlayers().stream()
                .filter(p -> p.getId().equals("0"))
                .findFirst()
                .orElseThrow();
        assertThat(loser.getForfeitReason(), is("RESIGNED"));
    }
}
