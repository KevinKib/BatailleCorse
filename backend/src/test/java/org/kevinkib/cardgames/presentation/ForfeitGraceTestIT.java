package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Under the {@code test} profile an e2e spec shortens the forfeit grace of one game over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ForfeitGraceTestIT {

    @LocalServerPort
    private int port;

    @Autowired
    private PresenceService presence;

    private final RestTemplate http = new RestTemplate();

    private ResponseEntity<String> put(String gameId, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + "/api/test/presence/" + gameId + "/forfeit-grace",
                HttpMethod.PUT, new HttpEntity<>(json, headers), String.class);
    }

    @Test
    void putShortensTheGraceOfThatGameOnly() {
        GameId id = GameId.generate();

        ResponseEntity<String> response = put(id.uuid().toString(), "{\"seconds\":2}");

        assertThat(response.getStatusCode().value(), is(204));
        assertThat(presence.forfeitGraceFor(id), is(Duration.ofSeconds(2)));
        assertThat(presence.forfeitGraceFor(GameId.generate()), is(Duration.ofSeconds(30)));
    }

    @Test
    void aMissingOrNonPositiveDurationIsRefusedWithA400() {
        String game = UUID.randomUUID().toString();

        assertThrows(HttpClientErrorException.BadRequest.class, () -> put(game, "{}"));
        assertThrows(HttpClientErrorException.BadRequest.class, () -> put(game, "{\"seconds\":0}"));
        assertThrows(HttpClientErrorException.BadRequest.class, () -> put("nope", "{\"seconds\":2}"));
    }
}
