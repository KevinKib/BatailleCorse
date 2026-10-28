package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceRenameTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        guestToken = service.joinRoom(id, "Bob").token();
    }

    private String nameAt(int seat) {
        return service.seats(id).get(seat).name();
    }

    @Test
    void givenOccupant_whenRenameSeat_thenNameChanged() {
        service.renameSeat(id, guestToken, "Robert");

        assertThat(nameAt(1), is("Robert"));
        assertThat(nameAt(0), is("Alice"));
    }

    @Test
    void givenPaddedName_whenRenameSeat_thenTrimmed() {
        service.renameSeat(id, hostToken, "  Alicia  ");

        assertThat(nameAt(0), is("Alicia"));
    }

    @Test
    void givenBlankName_whenRenameSeat_thenNameCleared() {
        service.renameSeat(id, guestToken, "   ");

        assertThat(nameAt(1), is((String) null));
    }

    @Test
    void givenNullName_whenRenameSeat_thenNameCleared() {
        service.renameSeat(id, hostToken, null);

        assertThat(nameAt(0), is((String) null));
    }

    @Test
    void givenLongName_whenRenameSeat_thenCutAtTwentyCharacters() {
        service.renameSeat(id, guestToken, "abcdefghijklmnopqrstuvwxyz");

        assertThat(nameAt(1), is("abcdefghijklmnopqrst"));
    }

    @Test
    void givenUnknownToken_whenRenameSeat_thenInvalidToken() {
        assertThrows(InvalidTokenException.class, () -> service.renameSeat(id, UUID.randomUUID().toString(), "X"));
    }

    @Test
    void givenGameStarted_whenRenameSeat_thenAlreadyStarted() {
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class, () -> service.renameSeat(id, guestToken, "Robert"));
        assertThat(nameAt(1), is("Bob"));
    }

    @Test
    void givenRename_whenLobbyViews_thenEveryHumanSeesTheNewName() {
        List<LobbyView> views = service.renameSeat(id, guestToken, "Robert").stream().map(AddressedLobbyView::view).toList();

        assertThat(views.size(), is(2));
        assertThat(views.get(0).players().get(1).name(), is("Robert"));
        assertThat(views.get(1).players().get(1).name(), is("Robert"));
    }
}
