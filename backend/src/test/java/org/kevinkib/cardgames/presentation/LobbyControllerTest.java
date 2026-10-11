package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.LobbyRenamePayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatOptionPayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.TieredFactory;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyControllerTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Response> payloads = new ArrayList<>();

        RecordingMessaging() {
            super(null, null);
        }


        SessionService resolver;

        /** Lobby views are addressed by token; the double maps it back to the seat to keep its assertions. */
        @Override
        public void sendToToken(GameId gameId, String token, Object payload) {
            sendToSeat(gameId, resolver.findPlayerIdByToken(gameId, token).orElseThrow(), payload);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }

        void clear() {
            seats.clear();
            payloads.clear();
        }
    }

    private SessionService sessionService;
    private RecordingMessaging messaging;
    private LobbyController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory(), new BatailleCorseFactory(), new TieredFactory())));
        messaging = new RecordingMessaging();
        messaging.resolver = sessionService;
        controller = new LobbyController(sessionService, messaging);
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
        assertThat(lobby.players().get(2).name(), is("Bot 1"));
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
}
