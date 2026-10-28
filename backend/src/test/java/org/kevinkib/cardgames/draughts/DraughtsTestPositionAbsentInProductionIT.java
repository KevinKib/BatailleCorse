package org.kevinkib.cardgames.draughts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.presentation.DraughtsTestPositionController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Without the {@code test} profile the position cannot be replaced: no controller, no route. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DraughtsTestPositionAbsentInProductionIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void theTestControllerIsNotABean() {
        assertThat(context.getBeansOfType(DraughtsTestPositionController.class).size(), is(0));
    }

    @Test
    void noRouteUnderApiTestDraughtsExistsButTheGameRoutesDo() {
        Set<String> patterns = mappings.getHandlerMethods().keySet().stream()
                .flatMap(m -> m.getPathPatternsCondition().getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns.stream().filter(p -> p.startsWith("/api/test/draughts")).count(), is(0L));
        assertThat(patterns, hasItems("/api/draughts/game/{id}", "/api/draughts/game/{id}/join",
                "/api/draughts/game/{id}/play-again"));
    }

    @Test
    void puttingAPositionIsNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThrows(HttpClientErrorException.NotFound.class, () -> new RestTemplate().exchange(
                "http://localhost:" + port + "/api/test/draughts/" + UUID.randomUUID() + "/position", HttpMethod.PUT,
                new HttpEntity<>("{\"layout\":\"" + ".".repeat(50) + "\",\"toMove\":\"white\"}", headers),
                String.class));
    }
}
