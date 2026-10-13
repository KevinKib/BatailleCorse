package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.AddressedLobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/** In a lobby (no game yet) a dropped connection is not a forfeit: the others are told, so the host can remove it. */
class PresenceInLobbyTest {

    private SessionService sessions;
    private PresenceService presence;
    private final List<List<AddressedLobbyView>> published = new ArrayList<>();
    private GameId id;
    private String guestToken;

    @BeforeEach
    void setUp() {
        sessions = new SessionService(new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        presence = new PresenceService(sessions, new InMemoryConnectionRegistry(), (deadline, task) -> () -> { },
                Clock.systemUTC(), new InMemoryForfeitLog(), new GameLifecycleBroadcasters(List.of()));
        presence.setLobbyPresenceListener((gameId, views) -> published.add(views));
        RoomCreated room = sessions.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        guestToken = sessions.joinRoom(id, "Bob").token();
    }

    @Test
    void givenAGuestInTheLobby_whenTheConnectionDrops_thenTheLobbyIsRepublishedWithTheSeatFlagged() {
        presence.onPresence("conn", id, guestToken);
        published.clear();

        presence.onDisconnect("conn");

        assertThat(published.size(), is(1));
        assertThat(published.get(0).get(0).view().players().get(1).disconnected(), is(true));
    }

    @Test
    void givenADroppedGuest_whenTheyComeBack_thenTheFlagIsClearedAndRepublished() {
        presence.onPresence("conn", id, guestToken);
        presence.onDisconnect("conn");
        published.clear();

        presence.onPresence("conn-2", id, guestToken);

        assertThat(published.size(), is(1));
        assertThat(published.get(0).get(0).view().players().get(1).disconnected(), is(false));
    }

    @Test
    void givenAFirstPresence_thenNothingIsRepublished() {
        presence.onPresence("conn", id, guestToken);

        assertThat(published, is(empty()));
    }
}
