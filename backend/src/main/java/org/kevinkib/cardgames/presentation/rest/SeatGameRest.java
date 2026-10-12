package org.kevinkib.cardgames.presentation.rest;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameAlreadyStartedException;
import org.kevinkib.cardgames.sessionmanagement.core.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomFullException;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The REST behaviour that every seat game shares: reading a seat's state by token, joining a room and playing
 * again. Each game's controller keeps its own mappings and calls these methods with its game type.
 *
 * <p>A plain helper (composition, no inheritance); it knows no game and keeps no state.
 */
public class SeatGameRest {

    private final SessionService sessionService;
    private final LobbyBroadcaster lobbyBroadcaster;

    public SeatGameRest(SessionService sessionService, LobbyBroadcaster lobbyBroadcaster) {
        this.sessionService = sessionService;
        this.lobbyBroadcaster = lobbyBroadcaster;
    }

    /**
     * The viewer's state: 403 without a token or with an unknown one, 404 for an unknown id or another game
     * type, {@code stateFor} applied to the running game and the viewer's seat, the lobby view before the start.
     */
    public ResponseEntity<?> state(String gameType, String id, String token,
                                   BiFunction<Game, PlayerId, Object> stateFor) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GameId gameId = gameIdOfType(gameType, id);
        if (gameId == null) {
            return ResponseEntity.notFound().build();
        }
        PlayerId seat = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        if (seat == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Game> game = sessionService.findGame(gameId);
        if (game.isPresent()) {
            return ResponseEntity.ok(stateFor.apply(game.get(), seat));
        }
        return ResponseEntity.ok(sessionService.lobbyView(gameId, token));
    }

    /** Claims the next free seat: 404 for an unknown id or another game type, 409 once started or full. */
    public ResponseEntity<JoinResponseDto> join(String gameType, String id, JoinGamePayload payload) {
        GameId gameId = gameIdOfType(gameType, id);
        if (gameId == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return joined(gameId, sessionService.joinRoom(gameId, nameOf(payload)));
        } catch (GameAlreadyStartedException | RoomFullException e) {
            return JoinRefusal.conflict(e);
        }
    }

    /** Reopens the room for a rematch and claims a seat: 404 as for {@link #join}, 409 when the room is full. */
    public ResponseEntity<JoinResponseDto> playAgain(String gameType, String id, JoinGamePayload payload) {
        GameId gameId = gameIdOfType(gameType, id);
        if (gameId == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return joined(gameId, sessionService.playAgain(gameId, nameOf(payload)));
        } catch (RoomFullException e) {
            return JoinRefusal.conflict(e);
        }
    }

    private ResponseEntity<JoinResponseDto> joined(GameId gameId, JoinResult result) {
        sessionService.touch(gameId);
        lobbyBroadcaster.send(gameId, result.views(), LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                "Player " + (result.playerId().id() + 1) + " joined.");
        return ResponseEntity.ok(new JoinResponseDto(result.playerId().id(), result.token()));
    }

    private static String nameOf(JoinGamePayload payload) {
        return (payload != null) ? payload.name() : null;
    }

    /** The id as a GameId when it names a session of this game type, null when malformed, unknown or of another game. */
    private GameId gameIdOfType(String gameType, String id) {
        try {
            GameId gameId = new GameId(id);
            return gameType.equals(sessionService.gameType(gameId)) ? gameId : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
