package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameAlreadyStartedException;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomFullException;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Optional;
import org.kevinkib.cardgames.sessionmanagement.core.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.SeatUnavailableException;
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
    private final LobbyBroadcaster lobbyBroadcaster;

    public BatailleCorseRestController(SessionService sessionService, GameMessagingService gameMessagingService,
                              ForfeitLog forfeitLog, LobbyBroadcaster lobbyBroadcaster) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
        this.forfeitLog = forfeitLog;
        this.lobbyBroadcaster = lobbyBroadcaster;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            GameId gameId = new GameId(id);
            if (!isBatailleCorse(gameId)) {
                return ResponseEntity.notFound().build();
            }
            BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
            return ResponseEntity.ok(BatailleCorseDto.from(game, forfeitLog.reasonsBySeat(gameId)));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Room model: the lobby view while the room is not started, the game state afterwards. */
    @GetMapping(value = "/game/{id}", params = "token")
    public ResponseEntity<?> getGameForSeat(@PathVariable String id, @RequestParam("token") String token) {
        GameId gameId;
        try {
            gameId = new GameId(id);
            if (!isBatailleCorse(gameId)) {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (sessionService.findPlayerIdByToken(gameId, token).isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Game> game = sessionService.findGame(gameId);
        if (game.isPresent()) {
            return ResponseEntity.ok(BatailleCorseDto.from((BatailleCorse) game.get(), forfeitLog.reasonsBySeat(gameId)));
        }
        return ResponseEntity.ok(sessionService.lobbyView(gameId, token));
    }

    private boolean isBatailleCorse(GameId gameId) {
        return BatailleCorseFactory.GAME_TYPE.equals(sessionService.gameType(gameId));
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(
            @PathVariable String id,
            @RequestBody(required = false) JoinGamePayload payload) {
        try {
            GameId gameId = new GameId(id);
            if (!isBatailleCorse(gameId)) {
                return ResponseEntity.notFound().build();
            }
            String name = (payload != null) ? payload.name() : null;
            if (sessionService.findGame(gameId).isEmpty()) {
                JoinResult joined = sessionService.joinRoom(gameId, name);
                sessionService.touch(gameId);
                lobbyBroadcaster.broadcast(gameId, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                        "Player " + (joined.playerId().id() + 1) + " joined.");
                return ResponseEntity.ok(new JoinResponseDto(joined.playerId().id(), joined.token()));
            }
            BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
            JoinResult result = sessionService.joinGame(gameId, name);

            Player joiner = game.getPlayerByIndex(result.playerId().id());
            SessionViewDto sessionView = SessionViewDto.from(sessionService.seats(gameId));
            Response broadcast = new SuccessResponse(
                    LifecycleEventType.JOIN.toString(),
                    new JoinEventData(PlayerIdDto.from(joiner), sessionView.players()),
                    "Player " + result.playerId().id() + " joined.",
                    BatailleCorseDto.from(game));
            gameMessagingService.sendToGame(id, broadcast);

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token()));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SeatUnavailableException | GameAlreadyStartedException | RoomFullException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
