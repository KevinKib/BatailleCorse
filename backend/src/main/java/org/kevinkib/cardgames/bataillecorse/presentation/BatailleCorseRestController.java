package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.rest.JoinRefusal;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameAlreadyStartedException;
import org.kevinkib.cardgames.sessionmanagement.core.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomFullException;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Bataille Corse on the room model: a seat token reads the lobby view or the game state, join and Play again
 * claim the next free seat. Every endpoint checks the game type (another game's id is a 404).
 */
@RestController
@RequestMapping("/api")
public class BatailleCorseRestController {

    private final SessionService sessionService;
    private final ForfeitLog forfeitLog;
    private final LobbyBroadcaster lobbyBroadcaster;

    public BatailleCorseRestController(SessionService sessionService, ForfeitLog forfeitLog,
                                       LobbyBroadcaster lobbyBroadcaster) {
        this.sessionService = sessionService;
        this.forfeitLog = forfeitLog;
        this.lobbyBroadcaster = lobbyBroadcaster;
    }

    /** The lobby view while the room is not started, the game state afterwards; a seat token is required. */
    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        Optional<GameId> gameId = batailleCorseId(id);
        if (gameId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (token == null || sessionService.findPlayerIdByToken(gameId.get(), token).isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Game> game = sessionService.findGame(gameId.get());
        if (game.isPresent()) {
            return ResponseEntity.ok(BatailleCorseDto.from((BatailleCorse) game.get(), forfeitLog.reasonsBySeat(gameId.get())));
        }
        return ResponseEntity.ok(sessionService.lobbyView(gameId.get(), token));
    }

    /** Claims the next free seat of a lobby; a started or full room is a conflict. */
    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        Optional<GameId> gameId = batailleCorseId(id);
        if (gameId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(seated(gameId.get(), sessionService.joinRoom(gameId.get(), nameOf(payload))));
        } catch (GameAlreadyStartedException | RoomFullException e) {
            return JoinRefusal.conflict(e);
        }
    }

    /** Reopens the lobby after a game (the bots and their tiers stay) and seats the caller in it. */
    @PostMapping("/game/{id}/play-again")
    public ResponseEntity<JoinResponseDto> playAgain(@PathVariable String id,
                                                     @RequestBody(required = false) JoinGamePayload payload) {
        Optional<GameId> gameId = batailleCorseId(id);
        if (gameId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(seated(gameId.get(), sessionService.playAgain(gameId.get(), nameOf(payload))));
        } catch (RoomFullException e) {
            return JoinRefusal.conflict(e);
        }
    }

    private JoinResponseDto seated(GameId gameId, JoinResult result) {
        sessionService.touch(gameId);
        lobbyBroadcaster.send(gameId, result.views(), LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                "Player " + (result.playerId().id() + 1) + " joined.");
        return new JoinResponseDto(result.playerId().id(), result.token());
    }

    private static String nameOf(JoinGamePayload payload) {
        return payload != null ? payload.name() : null;
    }

    /** The id of a Bataille Corse room; empty for a malformed or unknown id, or a room of another game. */
    private Optional<GameId> batailleCorseId(String id) {
        try {
            GameId gameId = new GameId(id);
            return BatailleCorseFactory.GAME_TYPE.equals(sessionService.gameType(gameId))
                    ? Optional.of(gameId) : Optional.empty();
        } catch (IllegalArgumentException | InvalidGameIdException e) {
            return Optional.empty();
        }
    }
}
