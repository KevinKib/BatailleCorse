package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.LobbyDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.GameAlreadyStartedException;
import org.kevinkib.cardgames.sessionmanagement.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.application.RoomFullException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
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

import java.util.Optional;

@RestController
@RequestMapping("/api/bullshit")
public class BullshitRestController {

    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;
    private final LobbyBroadcaster lobbyBroadcaster;

    public BullshitRestController(SessionService sessionService,
                                  BullshitStateBroadcaster broadcaster,
                                  LobbyBroadcaster lobbyBroadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.lobbyBroadcaster = lobbyBroadcaster;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GameId gameId;
        SessionGame session;
        try {
            gameId = new GameId(id);
            session = sessionService.getGameSession(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (!BullshitFactory.GAME_TYPE.equals(session.gameType())) {
            return ResponseEntity.notFound().build();
        }
        PlayerId seat = sessionService.findPlayerIdByToken(gameId, new SessionToken(token)).orElse(null);
        if (seat == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Game> game = sessionService.findGame(gameId);
        if (game.isPresent()) {
            return ResponseEntity.ok(BullshitDto.forViewer((Bullshit) game.get(), seat));
        }
        int min = sessionService.minPlayers(session.gameType());
        int max = sessionService.maxPlayers(session.gameType());
        return ResponseEntity.ok(LobbyDto.forViewer(session, min, max, seat));
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        GameId gameId;
        SessionGame session;
        try {
            gameId = new GameId(id);
            session = sessionService.getGameSession(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (!BullshitFactory.GAME_TYPE.equals(session.gameType())) {
            return ResponseEntity.notFound().build();
        }
        try {
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinRoom(gameId, name);
            sessionService.touch(gameId);

            SessionGame updated = sessionService.getGameSession(gameId);
            lobbyBroadcaster.broadcast(updated, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                    "Player " + (result.playerId().id() + 1) + " joined.");

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (GameAlreadyStartedException | RoomFullException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
