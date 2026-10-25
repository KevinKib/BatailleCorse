package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.presentation.dto.PublicLobbyListDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The snapshot of the public game list of one game type. It needs no token: the list is public and carries none.
 */
@RestController
@RequestMapping("/api/lobbies")
public class PublicLobbyRestController {

    private final SessionService sessionService;

    public PublicLobbyRestController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{gameType}")
    public ResponseEntity<PublicLobbyListDto> list(@PathVariable String gameType) {
        try {
            return ResponseEntity.ok(PublicLobbyListDto.from(sessionService.publicLobbies(gameType)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
