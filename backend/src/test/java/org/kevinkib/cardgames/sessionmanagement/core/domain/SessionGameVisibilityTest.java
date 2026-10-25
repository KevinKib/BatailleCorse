package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SessionGameVisibilityTest {

    private static SessionGame lobbyOf(int humans) {
        SessionGame lobby = SessionGame.create(GameId.generate(), 4, "kobo");
        for (int i = 0; i < humans; i++) {
            lobby.claimNextFreeSeat(null);
        }
        return lobby;
    }

    @Test
    void visibilityDefaultsToPublic() {
        assertThat(lobbyOf(1).visibility(), is(LobbyVisibility.PUBLIC));
    }

    @Test
    void visibilityCanBeChanged() {
        SessionGame lobby = lobbyOf(1);

        lobby.setVisibility(LobbyVisibility.PRIVATE);

        assertThat(lobby.visibility(), is(LobbyVisibility.PRIVATE));
    }

    @Test
    void visibilityIsKeptWhenASeatIsVacated() {
        SessionGame lobby = lobbyOf(3);
        lobby.setVisibility(LobbyVisibility.PRIVATE);

        lobby.vacate(new PlayerId(1));

        assertThat(lobby.visibility(), is(LobbyVisibility.PRIVATE));
    }

    @Test
    void visibilityIsKeptWhenASeatIsClaimedAheadOfBots() {
        SessionGame lobby = lobbyOf(1);
        lobby.claimBot(null);
        lobby.setVisibility(LobbyVisibility.PRIVATE);

        lobby.claimSeatAheadOfBots("Bob");

        assertThat(lobby.visibility(), is(LobbyVisibility.PRIVATE));
    }
}
