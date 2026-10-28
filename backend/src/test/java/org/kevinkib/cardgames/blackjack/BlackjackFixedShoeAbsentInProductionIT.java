package org.kevinkib.cardgames.blackjack;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.RandomShoeSource;
import org.kevinkib.cardgames.blackjack.domain.ScriptedShoeSource;
import org.kevinkib.cardgames.blackjack.presentation.BlackjackTestShoeController;
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

/** Without the {@code test} profile the shoe cannot be fixed: no controller, no route, a random shoe source. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlackjackFixedShoeAbsentInProductionIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void theTestControllerAndTheScriptedSourceAreNotBeans() {
        assertThat(context.getBeansOfType(BlackjackTestShoeController.class).size(), is(0));
        assertThat(context.getBeansOfType(ScriptedShoeSource.class).size(), is(0));
        assertThat(context.getBeansOfType(RandomShoeSource.class).size(), is(1));
    }

    @Test
    void noRouteUnderApiTestBlackjackExistsButTheGameRoutesDo() {
        Set<String> patterns = mappings.getHandlerMethods().keySet().stream()
                .flatMap(m -> m.getPathPatternsCondition().getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns.stream().filter(p -> p.startsWith("/api/test/blackjack")).count(), is(0L));
        assertThat(patterns, hasItems("/api/blackjack/game/{id}", "/api/blackjack/game/{id}/join",
                "/api/blackjack/game/{id}/play-again"));
    }

    @Test
    void fixingAShoeIsNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThrows(HttpClientErrorException.NotFound.class, () -> new RestTemplate().exchange(
                "http://localhost:" + port + "/api/test/blackjack/" + UUID.randomUUID() + "/deal", HttpMethod.PUT,
                new HttpEntity<>("{\"hands\":[[\"AS\"]]}", headers), String.class));
    }
}
