package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.presentation.api.CreateLobbyPayload;
import org.kevinkib.cardgames.presentation.dto.LobbyCreatedDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class LobbyRestControllerTest {

    private final SessionService sessions = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()),
            new GameFactories(List.of(new BatailleCorseFactory(), new BullshitFactory(), new KoboFactory(),
                    new org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory(),
                    new org.kevinkib.cardgames.mangola.domain.MangolaFactory(),
                    new org.kevinkib.cardgames.president.domain.PresidentFactory(),
                    new org.kevinkib.cardgames.draughts.domain.DraughtsFactory(),
                    new org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory(),
                    new org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory())));
    private final LobbyRestController controller = new LobbyRestController(sessions);

    @Test
    void create_makesARoomOfTheRequestedTypeAndReturnsTheHostToken() {
        ResponseEntity<LobbyCreatedDto> response =
                controller.create(new CreateLobbyPayload(BatailleCorseFactory.GAME_TYPE, "Alice"));

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        LobbyCreatedDto body = response.getBody();
        assertThat(body, notNullValue());
        GameId id = new GameId(body.gameId());
        assertThat(sessions.gameType(id), is(BatailleCorseFactory.GAME_TYPE));
        assertThat(sessions.findGame(id).isPresent(), is(false));
        assertThat(sessions.findPlayerIdByToken(id, body.token()).orElseThrow().id(), is(0));
        assertThat(sessions.seats(id).get(0).name(), is("Alice"));
    }

    @Test
    void create_worksForEveryRegisteredGameType() {
        for (String type : List.of(BullshitFactory.GAME_TYPE, KoboFactory.GAME_TYPE,
                org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory.GAME_TYPE,
                org.kevinkib.cardgames.mangola.domain.MangolaFactory.GAME_TYPE,
                org.kevinkib.cardgames.president.domain.PresidentFactory.GAME_TYPE,
                org.kevinkib.cardgames.draughts.domain.DraughtsFactory.GAME_TYPE,
                org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory.GAME_TYPE,
                org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory.GAME_TYPE)) {
            ResponseEntity<LobbyCreatedDto> response = controller.create(new CreateLobbyPayload(type, null));
            assertThat(response.getStatusCode(), is(HttpStatus.OK));
            assertThat(sessions.gameType(new GameId(response.getBody().gameId())), is(type));
        }
    }

    @Test
    void create_withAnUnknownMissingOrBlankTypeAnswers404() {
        assertThat(controller.create(new CreateLobbyPayload("chess", "A")).getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(controller.create(new CreateLobbyPayload(null, "A")).getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(controller.create(new CreateLobbyPayload(" ", "A")).getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(controller.create(null).getStatusCode(), is(HttpStatus.NOT_FOUND));
    }
}
