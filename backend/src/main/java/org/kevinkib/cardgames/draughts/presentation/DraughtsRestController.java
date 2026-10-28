package org.kevinkib.cardgames.draughts.presentation;

import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.rest.SeatGameRest;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/draughts")
public class DraughtsRestController {

    private final SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster;
    private final SeatGameRest rest;

    public DraughtsRestController(SessionService sessionService,
                                  SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster,
                                  LobbyBroadcaster lobbyBroadcaster) {
        this.broadcaster = broadcaster;
        this.rest = new SeatGameRest(sessionService, lobbyBroadcaster);
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        return rest.state(DraughtsFactory.GAME_TYPE, id, token,
                (game, seat) -> broadcaster.stateFor((Draughts) game, seat));
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        return rest.join(DraughtsFactory.GAME_TYPE, id, payload);
    }

    @PostMapping("/game/{id}/play-again")
    public ResponseEntity<JoinResponseDto> playAgain(@PathVariable String id,
                                                     @RequestBody(required = false) JoinGamePayload payload) {
        return rest.playAgain(DraughtsFactory.GAME_TYPE, id, payload);
    }
}
