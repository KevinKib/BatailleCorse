package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameRestController {

    private final SessionService sessionService;

    public GameRestController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            BatailleCorse game = sessionService.getGame(new BatailleCorseId(id));
            return ResponseEntity.ok(new BatailleCorseDto(game));
        } catch (InvalidGameIdException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
