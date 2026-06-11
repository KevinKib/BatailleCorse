package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.JoinResult;
import org.kevinkib.bataillecorse.sessionmanagement.application.SeatUnavailableException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.JoinGamePayload;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.JoinResponseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SessionViewDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.JoinEventData;
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
public class GameRestController {

    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    public GameRestController(SessionService sessionService, GameMessagingService gameMessagingService,
                              ForfeitReasonRegistry forfeitReasonRegistry) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            BatailleCorseId gameId = new BatailleCorseId(id);
            BatailleCorse game = sessionService.getGame(gameId);
            return ResponseEntity.ok(BatailleCorseDto.from(game, forfeitReasonRegistry.reasonsBySeat(gameId)));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/game/{id}/session")
    public ResponseEntity<SessionViewDto> getSession(@PathVariable String id) {
        try {
            BatailleCorseId gameId = new BatailleCorseId(id);
            List<SessionPlayer> seats = sessionService.getSeats(gameId);
            return ResponseEntity.ok(SessionViewDto.from(seats));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(
            @PathVariable String id,
            @RequestBody(required = false) JoinGamePayload payload) {
        try {
            BatailleCorseId gameId = new BatailleCorseId(id);
            BatailleCorse game = sessionService.getGame(gameId);
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinGame(gameId, name);

            Player joiner = game.getPlayerByIndex(result.playerId().id());
            SessionViewDto sessionView = SessionViewDto.from(sessionService.getSeats(gameId));
            Response broadcast = new SuccessResponse(
                    EventType.JOIN,
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
