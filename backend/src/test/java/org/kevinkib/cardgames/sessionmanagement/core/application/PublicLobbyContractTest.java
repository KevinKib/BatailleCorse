package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.sessionmanagement.core.domain.LobbyVisibility;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Shape of the public game list interfaces added ahead of their behaviour: stubs only. */
class PublicLobbyContractTest {

    private SessionService service;
    private RoomCreated room;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        room = service.createRoom("bullshit", "Alice");
    }

    @Test
    void visibilityIsPublicOrPrivate() {
        assertThat(List.of(LobbyVisibility.values()), contains(LobbyVisibility.PUBLIC, LobbyVisibility.PRIVATE));
    }

    @Test
    void publicLobbyStatesAreOpenFullAndInPlay() {
        assertThat(List.of(PublicLobbyState.values()),
                contains(PublicLobbyState.OPEN, PublicLobbyState.FULL, PublicLobbyState.IN_PLAY));
    }

    @Test
    void publicLobbiesIsAnEmptyListOfVersionZeroForNow() {
        PublicLobbyList list = service.publicLobbies("bullshit");

        assertThat(list.gameType(), is("bullshit"));
        assertThat(list.lobbies().isEmpty(), is(true));
        assertThat(list.total(), is(0));
        assertThat(list.truncated(), is(false));
        assertThat(list.version(), is(0L));
    }

    @Test
    void setPrivateIsNotImplementedYet() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.setPrivate(new GameId(room.gameId()), room.hostToken(), true));
    }

    @Test
    void theListenerIsStoredAndNeverCalledYet() {
        AtomicInteger calls = new AtomicInteger();
        service.setPublicLobbiesListener(type -> calls.incrementAndGet());

        service.createRoom("bullshit", "Bob");
        service.joinRoom(new GameId(room.gameId()), "Carol");

        assertThat(calls.get(), is(0));
    }

    @Test
    void createRoomAcceptsThePrivateFlagWithoutStoringItYet() {
        RoomCreated created = service.createRoom("bullshit", "Dana", GameOptions.none(), true);

        assertThat(service.lobbyView(new GameId(created.gameId()), created.hostToken()).privateGame(), is(false));
    }

    @Test
    void theLobbyViewCarriesPrivateGameFalse() {
        assertThat(service.lobbyView(new GameId(room.gameId()), room.hostToken()).privateGame(), is(false));
    }
}
