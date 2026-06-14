package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.presentation.api.CreateGamePayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerDto;
import org.kevinkib.cardgames.presentation.dto.SessionViewDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.CreateEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameRestControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private BatailleCorseWebSocketController wsController;

    private RestTemplate restTemplate = new RestTemplate();

    private static HttpEntity<String> jsonBody(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    @Test
    void givenUnknownId_whenGetGame_thenReturns404() {
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/game/unknown-id",
                    String.class);
            throw new AssertionError("Expected 404 but request succeeded");
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void givenExistingGame_whenGetGame_thenReturnsGameStateWithTwoPlayers() {
        Response createResponse = wsController.createGame(null);
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<BatailleCorseDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId,
                BatailleCorseDto.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        BatailleCorseDto body = response.getBody();
        assertThat(body, notNullValue());

        List<PlayerDto> players = body.getPlayers();
        assertThat(players, hasSize(2));
        for (PlayerDto player : players) {
            assertThat(player.getNbCards(), is(26));
        }
        assertThat(body.getPile().getCards(), empty());
    }

    @Test
    void givenCreate_whenCreateGame_thenResponseIncludesTokensForBothPlayers() {
        Response createResponse = wsController.createGame(null);
        CreateEventData createData = (CreateEventData) createResponse.getEventData();

        assertThat(createData.tokens(), hasKey(0));
        assertThat(createData.tokens(), hasKey(1));
        assertThat(createData.tokens().get(0), notNullValue());
        assertThat(createData.tokens().get(1), notNullValue());
        assertThat(createData.tokens().get(0), not(equalTo(createData.tokens().get(1))));
    }

    @Test
    void givenMultiplayerCreate_whenCreateGame_thenResponseIncludesOnlyTokenForSeatZero() {
        Response createResponse = wsController.createGame(new CreateGamePayload(GameMode.MULTIPLAYER, null));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();

        assertThat(createData.tokens(), hasKey(0));
        assertThat(createData.tokens(), not(hasKey(1)));
    }

    @Test
    void givenMultiplayerGame_whenJoin_thenReturnsJoinerTokenForSeatOne() {
        Response createResponse = wsController.createGame(new CreateGamePayload(GameMode.MULTIPLAYER, null));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<JoinResponseDto> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/join",
                jsonBody("{}"), JoinResponseDto.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), notNullValue());
        assertThat(response.getBody().playerId(), is(1));
        assertThat(response.getBody().token(), notNullValue());
    }

    @Test
    void givenSoloGame_whenJoin_thenReturns409() {
        Response createResponse = wsController.createGame(null);
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        try {
            restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/game/" + gameId + "/join",
                    jsonBody("{}"), JoinResponseDto.class);
            throw new AssertionError("Expected 409 but request succeeded");
        } catch (HttpClientErrorException.Conflict e) {
            assertThat(e.getStatusCode(), is(HttpStatus.CONFLICT));
        }
    }

    @Test
    void givenUnknownGame_whenJoin_thenReturns404() {
        try {
            restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/game/unknown-id/join",
                    jsonBody("{}"), JoinResponseDto.class);
            throw new AssertionError("Expected 404 but request succeeded");
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void givenMultiplayerGame_whenGetSession_thenSeatZeroJoinedWithNameAndSeatOnePending() {
        Response createResponse = wsController.createGame(
                new CreateGamePayload(GameMode.MULTIPLAYER, "Alice"));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<SessionViewDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/session",
                SessionViewDto.class);

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
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/game/unknown-id/session",
                    String.class);
            throw new AssertionError("Expected 404 but request succeeded");
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void givenMultiplayerGame_whenJoinWithName_thenSessionShowsBothNames() {
        Response createResponse = wsController.createGame(
                new CreateGamePayload(GameMode.MULTIPLAYER, "Alice"));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<String> request =
                new org.springframework.http.HttpEntity<>("{\"name\":\"Bob\"}", headers);

        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/join",
                request, JoinResponseDto.class);

        SessionViewDto session = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/session",
                SessionViewDto.class).getBody();
        assertThat(session.players().get(0).name(), is("Alice"));
        assertThat(session.players().get(1).name(), is("Bob"));
    }
}
