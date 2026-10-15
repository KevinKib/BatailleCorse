package org.kevinkib.cardgames.president;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.president.domain.DeckSource;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.presentation.PresidentTestDealController;
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

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Without the {@code test} profile the deal cannot be fixed: no controller, no route, a random source. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PresidentFixedDealAbsentInProductionIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void theTestControllerIsNotAMappingAndTheSourceIsRandom() {
        assertThat(context.getBeansOfType(PresidentTestDealController.class).size(), is(0));
        assertThat(context.getBeansOfType(DeckSource.class).values().stream()
                .anyMatch(RandomDeckSource.class::isInstance), is(true));
        assertThat(context.getBeansOfType(org.kevinkib.cardgames.president.domain.ScriptedDeckSource.class).size(),
                is(0));
    }

    @Test
    void noRouteUnderApiTestExists() {
        Set<String> patterns = mappings.getHandlerMethods().keySet().stream()
                .flatMap(m -> m.getPathPatternsCondition().getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns.stream().filter(p -> p.startsWith("/api/test")).count(), is(0L));
        assertThat(patterns, hasItems("/api/president/game/{id}", "/api/president/game/{id}/join",
                "/api/president/game/{id}/play-again"));
    }

    @Test
    void puttingADealIsNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThrows(HttpClientErrorException.NotFound.class, () -> new RestTemplate().exchange(
                "http://localhost:" + port + "/api/test/president/" + UUID.randomUUID() + "/deal", HttpMethod.PUT,
                new HttpEntity<>("{\"cards\":[\"KH\"]}", headers), String.class));
    }
}
