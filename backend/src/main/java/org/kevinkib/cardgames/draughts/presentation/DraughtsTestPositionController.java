package org.kevinkib.cardgames.draughts.presentation;

import java.util.Map;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.presentation.api.DraughtsPositionPayload;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end test support, present only under the Spring {@code test} profile: replaces the position of a started
 * game, so that a spec plays a short ending instead of a 50-move game, and tells the seats. Not mapped at all in any
 * other profile.
 */
@Profile("test")
@RestController
@RequestMapping("/api/test/draughts")
public class DraughtsTestPositionController {

    /** The event type the seats receive with the new state: not a game event, the client only takes the state. */
    static final String POSITION_EVENT = "POSITION";

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster;

    public DraughtsTestPositionController(SessionService sessionService,
                                          SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    @PutMapping("/{gameId}/position")
    public ResponseEntity<?> setPosition(@PathVariable String gameId,
                                         @RequestBody(required = false) DraughtsPositionPayload payload) {
        try {
            if (payload == null || payload.layout() == null || payload.toMove() == null) {
                throw new IllegalArgumentException("A body with a layout and a toMove is required");
            }
            Colour toMove = switch (payload.toMove()) {
                case "white" -> Colour.WHITE;
                case "black" -> Colour.BLACK;
                default -> throw new IllegalArgumentException("toMove is white or black");
            };
            DraughtsPosition position = DraughtsPosition.fromLayout(payload.layout(), toMove);
            GameId id = new GameId(gameId);
            Draughts game = sessionService.findGame(id)
                    .filter(Draughts.class::isInstance).map(Draughts.class::cast).orElse(null);
            if (game == null) {
                return ResponseEntity.notFound().build();
            }
            synchronized (game) {
                if (game.isFinished()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "The game is over"));
                }
                game.replacePosition(position);
                sessionService.touch(id);
                broadcaster.broadcast(game, POSITION_EVENT, new EmptyEventData(), "Position replaced.");
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
