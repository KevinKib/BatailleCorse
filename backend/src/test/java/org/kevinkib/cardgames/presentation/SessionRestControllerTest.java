package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.dto.SeatDto;
import org.kevinkib.cardgames.presentation.dto.SessionViewDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** The seats of a running game, as a table shows them: names, bots and the options of each bot. */
class SessionRestControllerTest {

    private final SessionService sessions = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()),
            new GameFactories(List.of(new BatailleCorseFactory())));
    private final SessionRestController controller = new SessionRestController(sessions);

    @Test
    void aRunningGameAgainstABot_listsTheBotWithItsTier_andTheHumanWithoutOptions() {
        RoomCreated room = sessions.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, BatailleCorseFactory.TIER_KEY, "3");
        sessions.startGame(id, room.hostToken(), BatailleCorse.class);

        ResponseEntity<SessionViewDto> response = controller.getSession(room.gameId());

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        List<SeatDto> players = response.getBody().players();
        assertThat(players.get(0), is(new SeatDto(0, "Alice", true, false, Map.of())));
        assertThat(players.get(1).bot(), is(true));
        assertThat(players.get(1).seatOptions(), is(Map.of(BatailleCorseFactory.TIER_KEY, "3")));
    }
}
