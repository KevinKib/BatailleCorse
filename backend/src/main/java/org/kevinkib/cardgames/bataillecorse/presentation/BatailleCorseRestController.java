package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.rest.SeatGameRest;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bataille Corse on the room model: a seat token reads the lobby view or the game state, join and Play again
 * claim the next free seat. Every endpoint checks the game type (another game's id is a 404).
 */
@RestController
@RequestMapping("/api")
public class BatailleCorseRestController {

    private final ForfeitLog forfeitLog;
    private final SeatGameRest rest;

    public BatailleCorseRestController(SessionService sessionService, ForfeitLog forfeitLog,
                                       LobbyBroadcaster lobbyBroadcaster) {
        this.forfeitLog = forfeitLog;
        this.rest = new SeatGameRest(sessionService, lobbyBroadcaster);
    }

    /** The lobby view while the room is not started, the game state afterwards; a seat token is required. */
    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        return rest.state(BatailleCorseFactory.GAME_TYPE, id, token, (game, seat) ->
                BatailleCorseDto.from((BatailleCorse) game, forfeitLog.reasonsBySeat(game.getId())));
    }

    /** Claims the next free seat of a lobby; a started or full room is a conflict. */
    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        return rest.join(BatailleCorseFactory.GAME_TYPE, id, payload);
    }

    /** Reopens the lobby after a game (the bots and their tiers stay) and seats the caller in it. */
    @PostMapping("/game/{id}/play-again")
    public ResponseEntity<JoinResponseDto> playAgain(@PathVariable String id,
                                                     @RequestBody(required = false) JoinGamePayload payload) {
        return rest.playAgain(BatailleCorseFactory.GAME_TYPE, id, payload);
    }
}
