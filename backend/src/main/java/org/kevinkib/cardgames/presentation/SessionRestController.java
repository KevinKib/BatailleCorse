package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.session.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.session.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.session.domain.SessionPlayer;
import org.kevinkib.cardgames.presentation.dto.SessionViewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SessionRestController {

    private final SessionService sessionService;

    public SessionRestController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/game/{id}/session")
    public ResponseEntity<SessionViewDto> getSession(@PathVariable String id) {
        try {
            GameId gameId = new GameId(id);
            List<SessionPlayer> seats = sessionService.getSeats(gameId);
            return ResponseEntity.ok(SessionViewDto.from(seats));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
