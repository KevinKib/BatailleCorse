package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.JoinResult;
import org.kevinkib.bataillecorse.sessionmanagement.application.SeatUnavailableException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.JoinResponseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.JoinEventData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameRestController {

    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;

    public GameRestController(SessionService sessionService, GameMessagingService gameMessagingService) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            BatailleCorse game = sessionService.getGame(new BatailleCorseId(id));
            return ResponseEntity.ok(BatailleCorseDto.from(game));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id) {
        try {
            BatailleCorseId gameId = new BatailleCorseId(id);
            BatailleCorse game = sessionService.getGame(gameId);
            JoinResult result = sessionService.joinGame(gameId);

            Player joiner = game.getPlayerByIndex(result.playerId().id());
            Response broadcast = new SuccessResponse(
                    EventType.JOIN,
                    new JoinEventData(PlayerIdDto.from(joiner)),
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
