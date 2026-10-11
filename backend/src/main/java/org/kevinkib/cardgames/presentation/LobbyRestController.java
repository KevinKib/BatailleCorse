package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.presentation.api.CreateLobbyPayload;
import org.kevinkib.cardgames.presentation.dto.LobbyCreatedDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Game-agnostic lobby creation. The host token goes back in the HTTP answer only: nothing is
 * published on a shared topic, so nobody else can learn it.
 */
@RestController
@RequestMapping("/api/lobby")
public class LobbyRestController {

    private final SessionService sessionService;

    public LobbyRestController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<LobbyCreatedDto> create(@RequestBody(required = false) CreateLobbyPayload payload) {
        if (payload == null || payload.gameType() == null || payload.gameType().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            RoomCreated room = sessionService.createRoom(payload.gameType(), payload.name());
            return ResponseEntity.ok(new LobbyCreatedDto(room.gameId(), room.hostToken()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
