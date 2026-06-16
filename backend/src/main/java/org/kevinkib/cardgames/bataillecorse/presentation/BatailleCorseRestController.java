package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SeatUnavailableException;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerIdDto;
import org.kevinkib.cardgames.presentation.dto.SessionViewDto;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.JoinEventData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BatailleCorseRestController {

    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;
    private final ForfeitLog forfeitLog;

    public BatailleCorseRestController(SessionService sessionService, GameMessagingService gameMessagingService,
                              ForfeitLog forfeitLog) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
        this.forfeitLog = forfeitLog;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            GameId gameId = new GameId(id);
            BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
            return ResponseEntity.ok(BatailleCorseDto.from(game, forfeitLog.reasonsBySeat(gameId)));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(
            @PathVariable String id,
            @RequestBody(required = false) JoinGamePayload payload) {
        try {
            GameId gameId = new GameId(id);
            BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinGame(gameId, name);

            Player joiner = game.getPlayerByIndex(result.playerId().id());
            SessionViewDto sessionView = SessionViewDto.from(sessionService.getSeats(gameId));
            Response broadcast = new SuccessResponse(
                    LifecycleEventType.JOIN.toString(),
                    new JoinEventData(PlayerIdDto.from(joiner), sessionView.players()),
                    "Player " + result.playerId().id() + " joined.",
                    BatailleCorseDto.from(game));
            gameMessagingService.sendToGame(id, broadcast);

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SeatUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
