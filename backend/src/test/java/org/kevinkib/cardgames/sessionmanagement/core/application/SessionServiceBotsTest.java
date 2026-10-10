package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.BotRemovalBlockedException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.NotABotSeatException;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceBotsTest {

    private SessionService service;
    private GameId id;
    private String hostToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
    }

    @Test
    void givenHost_whenAddBot_thenNextSeatIsABotNamedBotOne() {
        PlayerId seat = service.addBot(id, hostToken);

        assertThat(seat, is(new PlayerId(1)));
        assertThat(service.seats(id).get(1).bot(), is(true));
        assertThat(service.seats(id).get(1).name(), is("Bot 1"));
        assertThat(service.seats(id).get(1).joined(), is(true));
        assertThat(service.botSeats(id), is(Set.of(new PlayerId(1))));
    }

    @Test
    void givenNonHost_whenAddBot_thenNotHost() {
        String bobToken = service.joinRoom(id, "Bob").token();

        assertThrows(NotHostException.class, () -> service.addBot(id, bobToken));
    }

    @Test
    void givenUnknownToken_whenAddBot_thenNotHost() {
        assertThrows(NotHostException.class, () -> service.addBot(id, java.util.UUID.randomUUID().toString()));
    }

    @Test
    void givenGameStarted_whenAddBot_thenAlreadyStarted() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class, () -> service.addBot(id, hostToken));
    }

    @Test
    void givenFullRoom_whenAddBot_thenRoomFull() {
        for (int i = 0; i < 5; i++) {
            service.addBot(id, hostToken);
        }

        assertThrows(RoomFullException.class, () -> service.addBot(id, hostToken));
    }

    @Test
    void givenHostAlone_whenAddOneBot_thenGameCanStartWithTwoSeats() {
        service.addBot(id, hostToken);

        Bullshit game = (Bullshit) service.startGame(id, hostToken);

        assertThat(game.getPlayerIds().size(), is(2));
    }

    @Test
    void givenHostAloneWithoutBot_whenStart_thenNotEnoughPlayers() {
        assertThrows(NotEnoughPlayersException.class, () -> service.startGame(id, hostToken));
    }

    @Test
    void givenBot_whenRemoveBot_thenSeatFreedAndRoomRefreshable() {
        service.addBot(id, hostToken);

        service.removeBot(id, hostToken, 1);

        assertThat(service.seats(id).get(1).joined(), is(false));
        assertThat(service.botSeats(id), is(empty()));
    }

    @Test
    void givenNonHost_whenRemoveBot_thenNotHost() {
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        String bobToken = service.joinRoom(id, "Bob").token();

        assertThrows(NotHostException.class, () -> service.removeBot(id, bobToken, 2));
    }

    @Test
    void givenGameStarted_whenRemoveBot_thenAlreadyStarted() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class, () -> service.removeBot(id, hostToken, 1));
    }

    @Test
    void givenHumanSeat_whenRemoveBot_thenNotABotSeat() {
        assertThrows(NotABotSeatException.class, () -> service.removeBot(id, hostToken, 0));
    }

    @Test
    void givenHumanAfterTheBot_whenRemoveBot_thenBlocked() {
        service.addBot(id, hostToken);
        service.joinRoom(id, "Bob");

        assertThrows(BotRemovalBlockedException.class, () -> service.removeBot(id, hostToken, 1));
    }

    @Test
    void givenBotsAndHumans_whenLobbyViews_thenOnlyHumansReceiveAViewAndBotsAreFlagged() {
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);

        List<LobbyView> views = service.lobbyViews(id);

        assertThat(views.size(), is(1));
        LobbyView host = views.get(0);
        assertThat(host.mySeat(), is(0));
        assertThat(host.players().get(1).bot(), is(true));
        assertThat(host.players().get(1).joined(), is(true));
        assertThat(host.players().get(0).bot(), is(false));
        assertThat(host.canStart(), is(true));
        assertThat(host.removableBotSeats(), contains(1, 2));
    }

    @Test
    void givenAHumanSitsAfterABot_thenRemovableBotSeatsOnlyListsBotsWithNoHumanAfter() {
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.joinRoom(id, "Bob"); // seat 3

        assertThat(service.lobbyViews(id).get(0).removableBotSeats(), is(empty()));
    }

    @Test
    void givenNonHostViewer_thenRemovableBotSeatsIsEmpty() {
        service.addBot(id, hostToken);
        String bobToken = service.joinRoom(id, "Bob").token();

        assertThat(service.lobbyView(id, bobToken).removableBotSeats(), is(empty()));
    }

    @Test
    void givenBots_thenNoPublishedViewContainsTheBotInternalToken() {
        service.addBot(id, hostToken);
        PlayerId botSeat = new PlayerId(1);

        assertThrows(IllegalArgumentException.class, () -> service.tokenForSeat(id, botSeat));
        assertThat(service.findPlayerIdByToken(id, hostToken).get(), is(new PlayerId(0)));
        assertThat(service.lobbyViews(id).toString(), not(containsString("token")));
        assertThat(service.seats(id).toString(), not(containsString("token")));
    }

    @Test
    void givenGameWithBots_whenPlayAgain_thenLobbyHasNoBots() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);

        service.playAgain(id, "Alice");

        assertThat(service.botSeats(id), is(empty()));
        assertThat(service.seats(id).get(1).joined(), is(false));
    }
}
