package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * End-to-end test support, present only under the Spring {@code test} profile: shortens the forfeit grace of one
 * game so a forfeit spec does not wait for the profile's default. Not mapped at all in any other profile.
 */
@Profile("test")
@RestController
@RequestMapping("/api/test/presence")
public class ForfeitGraceTestController {

    public record ForfeitGracePayload(Integer seconds) { }

    private final PresenceService presence;

    public ForfeitGraceTestController(PresenceService presence) {
        this.presence = presence;
    }

    @PutMapping("/{gameId}/forfeit-grace")
    public ResponseEntity<?> shorten(@PathVariable String gameId, @RequestBody(required = false) ForfeitGracePayload payload) {
        try {
            if (payload == null || payload.seconds() == null || payload.seconds() < 1) {
                throw new IllegalArgumentException("A positive number of seconds is required");
            }
            presence.overrideForfeitGrace(new GameId(gameId), Duration.ofSeconds(payload.seconds()));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
