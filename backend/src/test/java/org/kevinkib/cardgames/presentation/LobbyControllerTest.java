package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.LobbyRenamePayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatOptionPayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.TieredFactory;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.application.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyControllerTest {

    
    /** Records the forfeits the presence service announces. */
    private static final class RecordingBroadcaster implements GameLifecycleBroadcaster {
        final List<String> events = new ArrayList<>();

        @Override public boolean supports(Game game) { return true; }
        @Override public void disconnected(Game game, PlayerId player, long deadlineEpochMs) { }
        @Override public void reconnected(Game game, PlayerId player) { }
        @Override public void forfeited(Game game, PlayerId player, ForfeitReason reason) { events.add(reason + " " + player.id()); }
    }

    private SessionService sessionService;
    private RecordingBroadcaster broadcaster;
    private RecordingMessaging messaging;
    private LobbyController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory(), new BatailleCorseFactory(), new TieredFactory(),
                        new KoboFactory(), new ThreeMenMorrisFactory(),
                        new org.kevinkib.cardgames.mangola.domain.MangolaFactory(),
                        new org.kevinkib.cardgames.president.domain.PresidentFactory(),
                        new org.kevinkib.cardgames.draughts.domain.DraughtsFactory(),
                        new org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory(),
                    new org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory())));
        messaging = new RecordingMessaging();
        messaging.resolvingTokensWith(sessionService);
        broadcaster = new RecordingBroadcaster();
        PresenceService presence = new PresenceService(sessionService, new InMemoryConnectionRegistry(),
                (deadline, task) -> () -> { }, Clock.systemUTC(), new InMemoryForfeitLog(),
                new GameLifecycleBroadcasters(List.of(broadcaster)));
        controller = new LobbyController(sessionService, messaging, presence);
    }

    private static LobbyView lobbyOf(Response response) {
        return (LobbyView) response.getState();
    }

    @Test
    void givenHostOfBullshit_whenAddBot_thenEveryHumanSeatReceivesALobbyWithTheBot() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        Response response = messaging.payloads.get(0);
        assertThat(response.isSuccess(), is(true));
        assertThat(response.getEventType(), is("JOIN"));
        LobbyView lobby = lobbyOf(response);
        assertThat(lobby.players().get(2).bot(), is(true));
        assertThat(lobby.players().get(2).name(), is((String) null));
    }

    @Test
    void givenHostOfBatailleCorse_whenAddBot_thenTheLobbyHasTheBot() {
        RoomCreated room = sessionService.createRoom("bataille-corse", "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).bot(), is(true));
    }

    @Test
    void givenHostOfKobo_whenAddBot_thenTheLobbyHasTheBotWithItsLevelOption() {
        RoomCreated room = sessionService.createRoom(KoboFactory.GAME_TYPE, "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).bot(), is(true));
        assertThat(sessionService.botSeats(new GameId(room.gameId())), is(java.util.Set.of(new PlayerId(1))));
    }

    @Test
    void givenHostOfThreeMenMorris_whenAddBot_thenTheLobbyHasTheBot() {
        RoomCreated room = sessionService.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).bot(), is(true));
    }

    @Test
    void givenHostOfPresident_whenAddBot_thenTheLobbyHasTheBot() {
        RoomCreated room = sessionService.createRoom(
                org.kevinkib.cardgames.president.domain.PresidentFactory.GAME_TYPE, "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).bot(), is(true));
        assertThat(sessionService.botSeats(new GameId(room.gameId())), is(java.util.Set.of(new PlayerId(1))));
    }

    @Test
    void givenHostOfMangola_whenAddBot_thenTheComputerTakesTheGuestSeat() {
        RoomCreated room = sessionService.createRoom(org.kevinkib.cardgames.mangola.domain.MangolaFactory.GAME_TYPE, "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.payloads.get(0).isSuccess(), is(true));
        assertThat(sessionService.botSeats(new GameId(room.gameId())), is(java.util.Set.of(new PlayerId(1))));
    }

    @Test
    void givenHostOfDraughts_whenAddBot_thenTheComputerTakesTheGuestSeat() {
        RoomCreated room = sessionService.createRoom(
                org.kevinkib.cardgames.draughts.domain.DraughtsFactory.GAME_TYPE, "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.payloads.get(0).isSuccess(), is(true));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).bot(), is(true));
        assertThat(sessionService.botSeats(new GameId(room.gameId())), is(java.util.Set.of(new PlayerId(1))));
    }

    @Test
    void givenNonHost_whenAddBot_thenErrorToTheActingSeatOnly() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), bobToken));

        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(sessionService.botSeats(new GameId(room.gameId())).isEmpty(), is(true));
    }

    @Test
    void givenUnknownToken_whenAddBot_thenIgnored() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), UUID.randomUUID().toString()));

        assertThat(messaging.seats.isEmpty(), is(true));
    }

    @Test
    void givenStartedGame_whenAddBot_thenErrorToTheHost() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        sessionService.startGame(new GameId(room.gameId()), room.hostToken());
        messaging.clear();

        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenBot_whenHostRemovesIt_thenLobbyRefreshWithoutTheBot() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));
        messaging.clear();

        controller.removeBot(new LobbySeatPayload(room.gameId(), room.hostToken(), 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        LobbyView lobby = lobbyOf(messaging.payloads.get(0));
        assertThat(lobby.players().get(1).joined(), is(false));
        assertThat(lobby.players().get(1).bot(), is(false));
    }

    @Test
    void givenHumanAfterTheBot_whenHostRemovesIt_thenRefusedWithAnError() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        messaging.clear();

        controller.removeBot(new LobbySeatPayload(room.gameId(), room.hostToken(), 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(sessionService.botSeats(new GameId(room.gameId())).size(), is(1));
    }

    @Test
    void givenNonHost_whenRemoveBot_thenErrorToTheActingSeat() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();

        controller.removeBot(new LobbySeatPayload(room.gameId(), bobToken, 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(2))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenGuest_whenRename_thenEverySeatReceivesTheNewName() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();

        controller.rename(new LobbyRenamePayload(room.gameId(), bobToken, "Robert"));

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).name(), is("Robert"));
        assertThat(lobbyOf(messaging.payloads.get(1)).players().get(1).name(), is("Robert"));
    }

    @Test
    void givenStartedGame_whenRename_thenErrorToTheActingSeat() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        sessionService.startGame(new GameId(room.gameId()), room.hostToken());
        messaging.clear();

        controller.rename(new LobbyRenamePayload(room.gameId(), room.hostToken(), "Al"));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenHostAndBot_whenSetSeatOption_thenEverySeatSeesIt() {
        RoomCreated room = sessionService.createRoom("tiered", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));
        messaging.clear();

        controller.setSeatOption(new LobbySeatOptionPayload(room.gameId(), room.hostToken(), 2, "tier", "2"));

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        assertThat(lobbyOf(messaging.payloads.get(1)).players().get(2).seatOptions(), is(Map.of("tier", "2")));
    }

    @Test
    void givenInvalidSeatOption_whenSetSeatOption_thenErrorToTheHostOnly() {
        RoomCreated room = sessionService.createRoom("tiered", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));
        messaging.clear();

        controller.setSeatOption(new LobbySeatOptionPayload(room.gameId(), room.hostToken(), 2, "tier", "42"));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenGuest_whenLeave_thenOnlyTheRemainingSeatsReceiveTheFreeSeat() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();

        controller.leave(new GameActionPayload(room.gameId(), bobToken));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(lobbyOf(messaging.payloads.get(0)).players().get(1).joined(), is(false));
    }

    @Test
    void givenHostAndGuest_whenHostLeaves_thenTheGuestReceivesAViewAsSeatZeroWithStart() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        sessionService.joinRoom(new GameId(room.gameId()), "Carol");
        messaging.clear();

        controller.leave(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        LobbyView bobView = lobbyOf(messaging.payloads.get(0));
        assertThat(bobView.mySeat(), is(0));
        assertThat(bobView.canStart(), is(true));
        assertThat(bobView.players().get(0).name(), is("Bob"));
    }

    @Test
    void givenOnlyBotsWouldRemain_whenHostLeaves_thenNothingIsSentAndTheLobbyIsGone() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));
        messaging.clear();

        controller.leave(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats.isEmpty(), is(true));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> sessionService.joinRoom(new GameId(room.gameId()), "Bob"));
    }

    @Test
    void givenHost_whenRemoveSeat_thenRemainingSeatsGetTheViewAndTheRemovedGetsAClearNotice() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        sessionService.joinRoom(new GameId(room.gameId()), "Carol");
        messaging.clear();

        controller.removeSeat(new LobbySeatPayload(room.gameId(), room.hostToken(), 1));

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        assertThat(lobbyOf(messaging.payloads.get(1)).players().get(1).name(), is("Carol"));
        assertThat(messaging.detachedTokens, is(List.of(bobToken)));
        Response notice = messaging.detachedPayloads.get(0);
        assertThat(notice.getEventType(), is("REMOVED"));
        assertThat(notice.isSuccess(), is(true));
        assertThat(notice.getMessage(), is("You were removed from the lobby by the host"));
    }

    @Test
    void givenGuest_whenRemoveSeat_thenOnlyTheSenderIsRefusedAndNobodyIsRemoved() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        sessionService.joinRoom(new GameId(room.gameId()), "Carol");
        messaging.clear();

        controller.removeSeat(new LobbySeatPayload(room.gameId(), bobToken, 2));

        assertThat(messaging.seats, is(List.of(new PlayerId(1))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(messaging.detachedTokens.isEmpty(), is(true));
        assertThat(sessionService.lobbyView(new GameId(room.gameId()), room.hostToken()).players().get(2).joined(), is(true));
    }

    @Test
    void givenHost_whenRemoveOwnSeat_thenRefusedToTheHostAlone() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(new GameId(room.gameId()), "Bob");
        messaging.clear();

        controller.removeSeat(new LobbySeatPayload(room.gameId(), room.hostToken(), 0));

        assertThat(messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void givenUnknownToken_whenLeave_thenIgnored() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        messaging.clear();
        controller.leave(new GameActionPayload(room.gameId(), UUID.randomUUID().toString()));
        assertThat(messaging.seats.isEmpty(), is(true));
    }

    @Test
    void givenAStartedGame_whenAPlayerLeaves_thenTheSeatForfeitsAndNothingIsRefused() {
        // The Start reached the server before the Leave: the leaver has already torn down its client, so the
        // seat must not stay seated for the others to wait on. It resigns, like /app/forfeit.
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        String bobToken = sessionService.joinRoom(new GameId(room.gameId()), "Bob").token();
        sessionService.startGame(new GameId(room.gameId()), room.hostToken());
        messaging.clear();

        controller.leave(new GameActionPayload(room.gameId(), bobToken));

        assertThat(broadcaster.events, is(List.of("RESIGNED 1")));
        assertThat(messaging.seats.isEmpty(), is(true));
    }

    @Test
    void givenALobbyAlreadyRemoved_whenALateLeaveArrives_thenItIsIgnoredWithoutError() {
        RoomCreated room = sessionService.createRoom("bullshit", "Alice");
        controller.leave(new GameActionPayload(room.gameId(), room.hostToken()));
        messaging.clear();

        controller.leave(new GameActionPayload(room.gameId(), room.hostToken()));
        controller.addBot(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(messaging.seats.isEmpty(), is(true));
    }
}
