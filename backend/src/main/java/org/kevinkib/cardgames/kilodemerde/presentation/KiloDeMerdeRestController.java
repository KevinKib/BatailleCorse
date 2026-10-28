package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.rest.SeatGameRest;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Consumer;

@RestController
@RequestMapping("/api/kilo-de-merde")
public class KiloDeMerdeRestController {

    private final SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster;
    private final SeatGameRest rest;
    private final Consumer<GameId> onPlayAgain;

    /**
     * @param scriptedDecks present under the {@code test} profile only: a rematch must not replay the deal fixed
     *                      for the game it replaces
     */
    @Autowired
    public KiloDeMerdeRestController(SessionService sessionService,
                                     SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster,
                                     LobbyBroadcaster lobbyBroadcaster,
                                     ObjectProvider<ScriptedDeckSource> scriptedDecks) {
        this(sessionService, broadcaster, lobbyBroadcaster,
                (Consumer<GameId>) id -> scriptedDecks.ifAvailable(decks -> decks.clear(id)));
    }

    public KiloDeMerdeRestController(SessionService sessionService,
                                     SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster,
                                     LobbyBroadcaster lobbyBroadcaster,
                                     Consumer<GameId> onPlayAgain) {
        this.broadcaster = broadcaster;
        this.rest = new SeatGameRest(sessionService, lobbyBroadcaster);
        this.onPlayAgain = onPlayAgain;
    }

    public KiloDeMerdeRestController(SessionService sessionService,
                                     SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster,
                                     LobbyBroadcaster lobbyBroadcaster) {
        this(sessionService, broadcaster, lobbyBroadcaster, (Consumer<GameId>) id -> { });
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        return rest.state(KiloDeMerdeFactory.GAME_TYPE, id, token,
                (game, seat) -> broadcaster.stateFor((KiloDeMerde) game, seat));
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        return rest.join(KiloDeMerdeFactory.GAME_TYPE, id, payload);
    }

    @PostMapping("/game/{id}/play-again")
    public ResponseEntity<JoinResponseDto> playAgain(@PathVariable String id,
                                                     @RequestBody(required = false) JoinGamePayload payload) {
        ResponseEntity<JoinResponseDto> response = rest.playAgain(KiloDeMerdeFactory.GAME_TYPE, id, payload);
        if (response.getStatusCode().is2xxSuccessful()) {
            onPlayAgain.accept(new GameId(id));
        }
        return response;
    }
}
