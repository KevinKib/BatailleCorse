package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class SessionServicePlayAgainTest {

    private SessionService service;
    private GameId id;
    private String hostToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new TieredFactory(), new BullshitFactory())));
        RoomCreated room = service.createRoom(TieredFactory.GAME_TYPE, "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
    }

    @Test
    void givenBotsWithTiers_whenPlayAgain_thenBotsComeBackInOrderWithTheirTiers() {
        PlayerId first = service.addBot(id, hostToken);
        PlayerId second = service.addBot(id, hostToken);
        service.setSeatOption(id, hostToken, first.id(), "tier", "0");
        service.setSeatOption(id, hostToken, second.id(), "tier", "2");
        service.startGame(id, hostToken);

        service.playAgain(id, "Alice");

        assertThat(service.botSeats(id), is(Set.of(new PlayerId(1), new PlayerId(2))));
        assertThat(service.seatOptions(id, new PlayerId(1)), is(Map.of("tier", "0")));
        assertThat(service.seatOptions(id, new PlayerId(2)), is(Map.of("tier", "2")));
        assertThat(service.seats(id).get(1).name(), is("Bot 1"));
        assertThat(service.seats(id).get(2).name(), is("Bot 2"));
    }

    @Test
    void givenBots_whenPlayAgain_thenFirstCallerIsHostOnSeatZero() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        JoinResult result = service.playAgain(id, "Alice");

        assertThat(result.playerId(), is(new PlayerId(0)));
        assertThat(service.seats(id).get(0).bot(), is(false));
        assertThat(service.seats(id).get(0).name(), is("Alice"));
    }

    @Test
    void givenBotsAndTwoHumans_whenPlayAgain_thenSecondHumanSitsBeforeTheBots() {
        service.joinRoom(id, "Bob");
        service.addBot(id, hostToken);
        service.setSeatOption(id, hostToken, 2, "tier", "2");
        service.startGame(id, hostToken);

        service.playAgain(id, "Alice");
        JoinResult second = service.playAgain(id, "Bob");

        assertThat(second.playerId(), is(new PlayerId(1)));
        assertThat(service.botSeats(id), is(Set.of(new PlayerId(2))));
        assertThat(service.seatOptions(id, new PlayerId(2)), is(Map.of("tier", "2")));
        assertThat(service.seats(id).get(2).name(), is("Bot 1"));
        assertThat(service.seats(id).get(1).name(), is("Bob"));
    }

    @Test
    void givenBotsAndTwoHumans_whenBothPlayAgain_thenTheHostMayRemoveEveryBot() {
        service.joinRoom(id, "Bob");
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        JoinResult host = service.playAgain(id, "Bob");
        JoinResult guest = service.playAgain(id, "Alice");

        assertThat(service.lobbyView(id, host.token()).removableBotSeats(), contains(2, 3));
        assertThat(service.lobbyView(id, guest.token()).removableBotSeats(), is(empty()));
        service.removeBot(id, host.token(), 2);
        assertThat(service.botSeats(id), is(Set.of(new PlayerId(2))));
        assertThat(service.seats(id).get(1).name(), is("Alice"));
    }

    @Test
    void givenAReopenedLobby_whenAStrangerJoinsThroughTheLink_thenTheyStillTakeTheLowestFreeSeat() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);
        service.playAgain(id, "Alice");

        JoinResult stranger = service.joinRoom(id, "Carl");

        assertThat(stranger.playerId(), is(new PlayerId(2)));
    }

    @Test
    void givenBotsThenHumanInTheFormerGame_whenPlayAgain_thenBotsStillSitRightAfterSeatZero() {
        service.addBot(id, hostToken);
        service.joinRoom(id, "Bob");
        service.startGame(id, hostToken);

        service.playAgain(id, "Bob");

        assertThat(service.botSeats(id), is(Set.of(new PlayerId(1))));
        assertThat(service.seats(id).get(0).name(), is("Bob"));
    }

    @Test
    void givenBots_whenPlayAgain_thenEveryHumanGetsAFreshToken() {
        String formerGuest = service.joinRoom(id, "Bob").token();
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        JoinResult host = service.playAgain(id, "Alice");
        JoinResult guest = service.playAgain(id, "Bob");

        assertThat(host.token(), is(not(hostToken)));
        assertThat(guest.token(), is(not(formerGuest)));
    }

    @Test
    void givenBotsKeptAndLobbyAlreadyReopened_whenSecondPlayAgain_thenBotsAreNotDuplicated() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);
        service.playAgain(id, "Alice");

        service.playAgain(id, "Bob");

        assertThat(service.botSeats(id), is(Set.of(new PlayerId(2))));
    }

    @Test
    void givenBotsFillingEveryOtherSeat_whenPlayAgain_thenLobbyIsAsFullAsBefore() {
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        service.playAgain(id, "Alice");

        assertThat(service.botSeats(id).size(), is(3));
    }
}
