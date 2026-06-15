package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.application.SeatUnavailableException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bullshit")
public class BullshitRestController {

    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;

    public BullshitRestController(SessionService sessionService, BullshitStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BullshitDto> getGame(@PathVariable String id,
                                               @RequestParam(name = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            GameId gameId = new GameId(id);
            Bullshit game = sessionService.getGame(gameId, Bullshit.class);
            PlayerId seat = sessionService.findPlayerIdByToken(gameId, new SessionToken(token)).orElse(null);
            if (seat == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(BullshitDto.forViewer(game, seat));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        try {
            GameId gameId = new GameId(id);
            Bullshit game = sessionService.getGame(gameId, Bullshit.class);
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinGame(gameId, name);
            sessionService.touch(gameId);

            broadcaster.broadcast(game, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                    "Player " + result.playerId().id() + " joined.");

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SeatUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
