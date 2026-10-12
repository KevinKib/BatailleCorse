package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.SessionViewDto;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The Bataille Corse REST endpoints over HTTP, on the room model (the lobby is created with POST /api/lobby). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatailleCorseRestControllerIT {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    private static HttpEntity<String> jsonBody(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> createLobby(String name) {
        return restTemplate.postForObject(url("/api/lobby"),
                jsonBody("{\"gameType\":\"bataille-corse\",\"name\":\"" + name + "\"}"), Map.class);
    }

    @Test
    void givenUnknownId_whenGetGame_thenReturns404() {
        HttpClientErrorException e = assertThrows(HttpClientErrorException.NotFound.class,
                () -> restTemplate.getForEntity(url("/api/game/unknown-id?token=t"), String.class));
        assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void givenLobby_whenGetWithoutToken_thenReturns403() {
        String gameId = createLobby("Alice").get("gameId");

        HttpClientErrorException e = assertThrows(HttpClientErrorException.Forbidden.class,
                () -> restTemplate.getForEntity(url("/api/game/" + gameId), String.class));
        assertThat(e.getStatusCode(), is(HttpStatus.FORBIDDEN));
    }

    @Test
    void givenLobby_whenJoin_thenReturnsTheSecondSeatAndItsOwnToken() {
        Map<String, String> lobby = createLobby("Alice");

        ResponseEntity<JoinResponseDto> joined = restTemplate.postForEntity(
                url("/api/game/" + lobby.get("gameId") + "/join"), jsonBody("{\"name\":\"Bob\"}"), JoinResponseDto.class);

        assertThat(joined.getStatusCode(), is(HttpStatus.OK));
        assertThat(joined.getBody().playerId(), is(1));
        assertThat(joined.getBody().token(), not(equalTo(lobby.get("token"))));
    }

    @Test
    void givenFullLobby_whenJoin_thenReturns409() {
        String gameId = createLobby("Alice").get("gameId");
        restTemplate.postForEntity(url("/api/game/" + gameId + "/join"), jsonBody("{}"), JoinResponseDto.class);

        HttpClientErrorException e = assertThrows(HttpClientErrorException.Conflict.class,
                () -> restTemplate.postForEntity(url("/api/game/" + gameId + "/join"), jsonBody("{}"), JoinResponseDto.class));
        assertThat(e.getStatusCode(), is(HttpStatus.CONFLICT));
    }

    @Test
    void givenUnknownGame_whenJoin_thenReturns404() {
        assertThrows(HttpClientErrorException.NotFound.class,
                () -> restTemplate.postForEntity(url("/api/game/00000000-0000-0000-0000-000000000000/join"),
                        jsonBody("{}"), JoinResponseDto.class));
    }

    @Test
    void givenLobby_whenGetSession_thenSeatZeroJoinedWithNameAndSeatOnePending() {
        String gameId = createLobby("Alice").get("gameId");

        ResponseEntity<SessionViewDto> response = restTemplate.getForEntity(
                url("/api/game/" + gameId + "/session"), SessionViewDto.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        SessionViewDto body = response.getBody();
        assertThat(body, notNullValue());
        assertThat(body.players(), hasSize(2));
        assertThat(body.players().get(0).joined(), is(true));
        assertThat(body.players().get(0).name(), is("Alice"));
        assertThat(body.players().get(1).joined(), is(false));
    }

    @Test
    void givenUnknownId_whenGetSession_thenReturns404() {
        assertThrows(HttpClientErrorException.NotFound.class,
                () -> restTemplate.getForEntity(url("/api/game/unknown-id/session"), String.class));
    }

    @Test
    void givenLobby_whenJoinWithName_thenSessionShowsBothNames() {
        String gameId = createLobby("Alice").get("gameId");

        restTemplate.postForEntity(url("/api/game/" + gameId + "/join"), jsonBody("{\"name\":\"Bob\"}"),
                JoinResponseDto.class);

        SessionViewDto session = restTemplate.getForEntity(url("/api/game/" + gameId + "/session"),
                SessionViewDto.class).getBody();
        assertThat(session.players().get(0).name(), is("Alice"));
        assertThat(session.players().get(1).name(), is("Bob"));
    }
}
