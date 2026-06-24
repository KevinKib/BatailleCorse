package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyViewTest {

    private SessionGame lobbyWith(int claimedSeats, int maxSeats) {
        List<PlayerId> seats = new java.util.ArrayList<>();
        for (int i = 0; i < maxSeats; i++) {
            seats.add(new PlayerId(i));
        }
        SessionGame lobby = SessionGame.create(GameId.generate(), seats, "bullshit");
        for (int i = 0; i < claimedSeats; i++) {
            lobby.claimSeat(new PlayerId(i), "P" + i);
        }
        return lobby;
    }

    @Test
    void givenHostViewerAtMin_whenForViewer_thenCanStartTrueAndNotStarted() {
        SessionGame lobby = lobbyWith(2, 6);

        LobbyView dto = LobbyView.forViewer(lobby, 2, 6, new PlayerId(0));

        assertThat(dto.started(), is(false));
        assertThat(dto.hostSeat(), is(0));
        assertThat(dto.mySeat(), is(0));
        assertThat(dto.minPlayers(), is(2));
        assertThat(dto.maxPlayers(), is(6));
        assertThat(dto.players().size(), is(6));
        assertThat(dto.players().get(0).joined(), is(true));
        assertThat(dto.players().get(2).joined(), is(false));
        assertThat(dto.canStart(), is(true));
    }

    @Test
    void givenNonHostViewer_whenForViewer_thenCanStartFalse() {
        SessionGame lobby = lobbyWith(2, 6);

        LobbyView dto = LobbyView.forViewer(lobby, 2, 6, new PlayerId(1));

        assertThat(dto.mySeat(), is(1));
        assertThat(dto.canStart(), is(false));
    }

    @Test
    void givenHostBelowMin_whenForViewer_thenCanStartFalse() {
        SessionGame lobby = lobbyWith(1, 6);

        LobbyView dto = LobbyView.forViewer(lobby, 2, 6, new PlayerId(0));

        assertThat(dto.canStart(), is(false));
    }

    @Test
    void givenLobbyWithSuitOption_whenForViewer_thenOptionsCarryClaimMode() {
        SessionGame lobby = SessionGame.create(
                GameId.generate(), 2, "bullshit", GameOptions.of(Map.of("claimMode", "suit")));

        LobbyView view = LobbyView.forViewer(lobby, 2, 6, new PlayerId(0));

        assertThat(view.options(), is(Map.of("claimMode", "suit")));
    }
}
