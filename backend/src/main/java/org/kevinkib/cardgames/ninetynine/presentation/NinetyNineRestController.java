package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
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
@RequestMapping("/api/ninety-nine")
public class NinetyNineRestController {

    private final SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster;
    private final SeatGameRest rest;

    public NinetyNineRestController(SessionService sessionService,
                                    SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster,
                                    LobbyBroadcaster lobbyBroadcaster) {
        this.broadcaster = broadcaster;
        this.rest = new SeatGameRest(sessionService, lobbyBroadcaster);
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        return rest.state(NinetyNineFactory.GAME_TYPE, id, token,
                (game, seat) -> broadcaster.stateFor((NinetyNine) game, seat));
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        return rest.join(NinetyNineFactory.GAME_TYPE, id, payload);
    }

    @PostMapping("/game/{id}/play-again")
    public ResponseEntity<JoinResponseDto> playAgain(@PathVariable String id,
                                                     @RequestBody(required = false) JoinGamePayload payload) {
        return rest.playAgain(NinetyNineFactory.GAME_TYPE, id, payload);
    }
}
