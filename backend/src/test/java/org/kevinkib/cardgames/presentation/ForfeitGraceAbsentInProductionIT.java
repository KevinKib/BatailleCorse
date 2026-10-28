package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Without the {@code test} profile the forfeit grace cannot be changed: no controller, no route. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForfeitGraceAbsentInProductionIT {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void theTestControllerIsNotABean() {
        assertThat(context.getBeansOfType(ForfeitGraceTestController.class).size(), is(0));
    }

    @Test
    void puttingAGraceIsNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThrows(HttpClientErrorException.NotFound.class, () -> new RestTemplate().exchange(
                "http://localhost:" + port + "/api/test/presence/" + UUID.randomUUID() + "/forfeit-grace",
                HttpMethod.PUT, new HttpEntity<>("{\"seconds\":2}", headers), String.class));
    }
}
