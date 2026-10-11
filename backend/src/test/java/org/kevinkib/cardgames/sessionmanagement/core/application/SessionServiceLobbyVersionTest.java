package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class SessionServiceLobbyVersionTest {

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

    private long hostVersion(List<AddressedLobbyView> views) {
        return views.stream().filter(v -> v.token().equals(hostToken)).findFirst().orElseThrow().view().lobbyVersion();
    }

    @Test
    void givenEveryKindOfMutation_thenTheLobbyVersionStrictlyIncreases() {
        List<Long> versions = new ArrayList<>();
        versions.add(service.lobbyView(id, hostToken).lobbyVersion());

        String bobToken = null;
        JoinResult bob = service.joinRoom(id, "Bob");
        bobToken = bob.token();
        versions.add(hostVersion(bob.views()));
        service.addBot(id, hostToken);
        versions.add(service.lobbyView(id, hostToken).lobbyVersion());
        versions.add(hostVersion(service.renameSeat(id, bobToken, "Bobby")));
        versions.add(hostVersion(service.updateOptions(id, hostToken, Map.of())));
        versions.add(hostVersion(service.setSeatOption(id, hostToken, 2, "tier", "2")));
        versions.add(hostVersion(service.removeBotViews(id, hostToken, 2)));

        for (int i = 1; i < versions.size(); i++) {
            assertThat("mutation " + i, versions.get(i), greaterThan(versions.get(i - 1)));
        }
    }

    @Test
    void givenAMutation_thenEachViewIsAddressedToItsOwnViewerToken() {
        JoinResult bob = service.joinRoom(id, "Bob");

        List<AddressedLobbyView> views = bob.views();

        assertThat(views.size(), is(2));
        for (AddressedLobbyView addressed : views) {
            assertThat(service.findPlayerIdByToken(id, addressed.token()).orElseThrow().id(),
                    is(addressed.view().mySeat()));
        }
    }

    @Test
    void givenAReopenedLobby_thenItsVersionContinuesFromTheOneItReplaces() {
        service.joinRoom(id, "Bob");
        long beforeReopen = service.lobbyView(id, hostToken).lobbyVersion();
        service.startGame(id, hostToken);

        JoinResult reopened = service.playAgain(id, "Alice");

        long after = reopened.views().stream().filter(v -> v.token().equals(reopened.token())).findFirst()
                .orElseThrow().view().lobbyVersion();
        assertThat(after, greaterThan(beforeReopen));
    }
}
