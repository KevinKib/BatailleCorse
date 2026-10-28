package org.kevinkib.cardgames.kilodemerde;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/** The public game list and the usage statistics work on the game type key: Kilo de merde needs nothing of its own. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KiloDeMerdePublicLobbyIT {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessionService;

    @Test
    void theLobbiesOfKiloDeMerdeAreListedUnderItsGameKey() {
        RoomCreated room = sessionService.createRoom("kilo-de-merde", "Alice");

        ResponseEntity<String> response = new RestTemplate()
                .getForEntity("http://localhost:" + port + "/api/lobbies/kilo-de-merde", String.class);

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), containsString(room.gameId()));
    }
}
