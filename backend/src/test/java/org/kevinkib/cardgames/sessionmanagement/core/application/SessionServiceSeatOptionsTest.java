package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.NotABotSeatException;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceSeatOptionsTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new TieredFactory(), new BullshitFactory())));
        RoomCreated room = service.createRoom(TieredFactory.GAME_TYPE, "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        guestToken = service.joinRoom(id, "Bob").token();
    }

    @Test
    void givenHostAndBot_whenSetSeatOption_thenStoredAndAnsweredByThePort() {
        PlayerId bot = service.addBot(id, hostToken);

        service.setSeatOption(id, hostToken, bot.id(), "tier", "2");

        assertThat(service.seatOptions(id, bot), is(Map.of("tier", "2")));
    }

    @Test
    void givenHumanSeat_whenSeatOptionsAsked_thenEmpty() {
        assertThat(service.seatOptions(id, new PlayerId(1)), is(Map.of()));
    }

    @Test
    void givenNonHost_whenSetSeatOption_thenNotHost() {
        PlayerId bot = service.addBot(id, hostToken);

        assertThrows(NotHostException.class, () -> service.setSeatOption(id, guestToken, bot.id(), "tier", "2"));
    }

    @Test
    void givenHumanSeat_whenSetSeatOption_thenNotABotSeat() {
        assertThrows(NotABotSeatException.class, () -> service.setSeatOption(id, hostToken, 1, "tier", "2"));
    }

    @Test
    void givenUnknownKeyOrValue_whenSetSeatOption_thenRefusedAndUnchanged() {
        PlayerId bot = service.addBot(id, hostToken);

        assertThrows(InvalidOptionException.class, () -> service.setSeatOption(id, hostToken, bot.id(), "speed", "2"));
        assertThrows(InvalidOptionException.class, () -> service.setSeatOption(id, hostToken, bot.id(), "tier", "9"));
        assertThat(service.seatOptions(id, bot), is(Map.of()));
    }

    @Test
    void givenGameStarted_whenSetSeatOption_thenAlreadyStarted() {
        PlayerId bot = service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class,
                () -> service.setSeatOption(id, hostToken, bot.id(), "tier", "2"));
    }

    @Test
    void givenGameWithoutSeatOptions_whenSetSeatOption_thenRefused() {
        RoomCreated room = service.createRoom("bullshit", "Carol");
        GameId plain = new GameId(room.gameId());
        PlayerId bot = service.addBot(plain, room.hostToken());

        assertThrows(InvalidOptionException.class,
                () -> service.setSeatOption(plain, room.hostToken(), bot.id(), "tier", "1"));
    }

    @Test
    void givenBotWithOption_whenAnEarlierBotIsRemoved_thenTheOptionFollowsTheShiftedBot() {
        PlayerId first = service.addBot(id, hostToken);
        PlayerId second = service.addBot(id, hostToken);
        service.setSeatOption(id, hostToken, second.id(), "tier", "2");

        service.removeBot(id, hostToken, first.id());

        assertThat(service.seatOptions(id, first), is(Map.of("tier", "2")));
        assertThat(service.seatOptions(id, second), is(Map.of()));
    }

    @Test
    void givenSeatOptionSet_whenLobbyViews_thenBotSeatCarriesItAndHumansCarryNone() {
        PlayerId bot = service.addBot(id, hostToken);
        List<LobbyView> views = service.setSeatOption(id, hostToken, bot.id(), "tier", "0").stream().map(AddressedLobbyView::view).toList();

        LobbyView view = views.get(1);
        assertThat(view.seatOptionDescriptors().size(), is(1));
        assertThat(view.players().get(bot.id()).seatOptions(), is(Map.of("tier", "0")));
        assertThat(view.players().get(0).seatOptions(), is(Map.of()));
    }
}
