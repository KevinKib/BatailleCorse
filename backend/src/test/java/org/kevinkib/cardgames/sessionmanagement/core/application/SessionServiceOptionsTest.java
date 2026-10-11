package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceOptionsTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory(), new BatailleCorseFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        guestToken = service.joinRoom(id, "Bob").token();
    }

    @Test
    void givenHost_whenUpdateOptions_thenStoredAndReturnedViewsCarryThem() {
        List<LobbyView> views = service.updateOptions(id, hostToken, Map.of("claimMode", "suit")).stream().map(AddressedLobbyView::view).toList();

        assertThat(views.size(), is(2));
        assertThat(views.get(0).options().get("claimMode"), is("suit"));
        assertThat(views.get(1).options().get("claimMode"), is("suit"));
        assertThat(service.lobbyView(id, guestToken).options().get("claimMode"), is("suit"));
    }

    @Test
    void givenTwoUpdates_whenUpdateOptions_thenMerged() {
        service.updateOptions(id, hostToken, Map.of("claimMode", "suit"));
        service.updateOptions(id, hostToken, Map.of("deckSize", "52"));

        Map<String, String> options = service.lobbyView(id, hostToken).options();
        assertThat(options.get("claimMode"), is("suit"));
        assertThat(options.get("deckSize"), is("52"));
    }

    @Test
    void givenGuest_whenUpdateOptions_thenNotHostAndUnchanged() {
        assertThrows(NotHostException.class,
                () -> service.updateOptions(id, guestToken, Map.of("claimMode", "suit")));

        assertThat(service.lobbyView(id, hostToken).options().get("claimMode"), is("rank"));
    }

    @Test
    void givenUnknownToken_whenUpdateOptions_thenNotHost() {
        assertThrows(NotHostException.class,
                () -> service.updateOptions(id, UUID.randomUUID().toString(), Map.of("claimMode", "suit")));
    }

    @Test
    void givenStartedGame_whenUpdateOptions_thenRefused() {
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class,
                () -> service.updateOptions(id, hostToken, Map.of("claimMode", "suit")));
    }

    @Test
    void givenInvalidValue_whenUpdateOptions_thenRefusedAndOptionsUnchanged() {
        service.updateOptions(id, hostToken, Map.of("claimMode", "suit"));

        assertThrows(InvalidOptionException.class,
                () -> service.updateOptions(id, hostToken, Map.of("deckSize", "40")));

        Map<String, String> options = service.lobbyView(id, hostToken).options();
        assertThat(options.get("claimMode"), is("suit"));
        assertThat(options.get("deckSize"), is("32"));
    }

    @Test
    void givenUnknownKey_whenUpdateOptions_thenRefusedAndOptionsUnchanged() {
        assertThrows(InvalidOptionException.class,
                () -> service.updateOptions(id, hostToken, Map.of("bogus", "x")));

        assertThat(service.lobbyView(id, hostToken).options().containsKey("bogus"), is(false));
    }

    @Test
    void givenBatailleCorseLobby_whenUpdateAnyKey_thenRefused() {
        RoomCreated room = service.createRoom("bataille-corse", "Alice");

        assertThrows(InvalidOptionException.class,
                () -> service.updateOptions(new GameId(room.gameId()), room.hostToken(), Map.of("claimMode", "suit")));
    }

    @Test
    void givenUpdatedOptions_whenPlayAgain_thenKept() {
        service.updateOptions(id, hostToken, Map.of("claimMode", "suit", "deckSize", "52"));
        service.startGame(id, hostToken);

        service.playAgain(id, "Alice");

        assertThat(service.lobbyViews(id).get(0).view().options(), is(Map.of("claimMode", "suit", "deckSize", "52")));
    }

    @Test
    void givenCreateRoomWithOptions_whenLobbyView_thenStaleClientOptionsStillWork() {
        RoomCreated room = service.createRoom("bullshit", "Carol",
                GameOptions.of(Map.of("claimMode", "suit", "deckSize", "52")));

        Map<String, String> options = service.lobbyView(new GameId(room.gameId()), room.hostToken()).options();

        assertThat(options.get("claimMode"), is("suit"));
        assertThat(options.get("deckSize"), is("52"));
    }

    @Test
    void givenCreateRoomWithInvalidValues_whenStart_thenStillDefaultsLeniently() {
        RoomCreated room = service.createRoom("bullshit", "Carol",
                GameOptions.of(Map.of("claimMode", "bogus", "deckSize", "40")));
        GameId gameId = new GameId(room.gameId());
        service.addBot(gameId, room.hostToken());

        assertThat(service.startGame(gameId, room.hostToken()).isFinished(), is(false));
    }
}
