package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyViewTest {

    private static final List<OptionDescriptor> NO_OPTIONS = List.of();
    private static final List<OptionDescriptor> BULLSHIT_OPTIONS = new BullshitFactory().optionDescriptors();

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

        LobbyView dto = LobbyView.forViewer(lobby, 2, 6, NO_OPTIONS, new PlayerId(0));

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

        LobbyView dto = LobbyView.forViewer(lobby, 2, 6, NO_OPTIONS, new PlayerId(1));

        assertThat(dto.mySeat(), is(1));
        assertThat(dto.canStart(), is(false));
    }

    @Test
    void givenHostBelowMin_whenForViewer_thenCanStartFalse() {
        SessionGame lobby = lobbyWith(1, 6);

        LobbyView dto = LobbyView.forViewer(lobby, 2, 6, NO_OPTIONS, new PlayerId(0));

        assertThat(dto.canStart(), is(false));
    }

    @Test
    void givenLobbyWithSuitOption_whenForViewer_thenOptionsCarryClaimMode() {
        SessionGame lobby = SessionGame.create(
                GameId.generate(), 2, "bullshit", GameOptions.of(Map.of("claimMode", "suit")));

        LobbyView view = LobbyView.forViewer(lobby, 2, 6, BULLSHIT_OPTIONS, new PlayerId(0));

        assertThat(view.options(), is(Map.of("claimMode", "suit", "deckSize", "32")));
    }

    @Test
    void givenDescriptors_whenForViewer_thenViewCarriesThem() {
        SessionGame lobby = lobbyWith(2, 6);

        LobbyView view = LobbyView.forViewer(lobby, 2, 6, BULLSHIT_OPTIONS, new PlayerId(0));

        assertThat(view.optionDescriptors(), is(BULLSHIT_OPTIONS));
    }

    @Test
    void givenHostViewer_whenForViewer_thenOnlyHostCanEditOptions() {
        SessionGame lobby = lobbyWith(2, 6);

        assertThat(LobbyView.forViewer(lobby, 2, 6, BULLSHIT_OPTIONS, new PlayerId(0)).canEditOptions(), is(true));
        assertThat(LobbyView.forViewer(lobby, 2, 6, BULLSHIT_OPTIONS, new PlayerId(1)).canEditOptions(), is(false));
    }

    @Test
    void givenGameWithoutOptions_whenForViewer_thenHostCannotEditAndNothingDeclared() {
        SessionGame lobby = lobbyWith(2, 2);

        LobbyView view = LobbyView.forViewer(lobby, 2, 2, NO_OPTIONS, new PlayerId(0));

        assertThat(view.optionDescriptors().isEmpty(), is(true));
        assertThat(view.canEditOptions(), is(false));
    }

    @Test
    void givenNoStoredOptions_whenForViewer_thenDefaultsFilledForMissingKeys() {
        SessionGame lobby = lobbyWith(2, 6);

        LobbyView view = LobbyView.forViewer(lobby, 2, 6, BULLSHIT_OPTIONS, new PlayerId(0));

        assertThat(view.options(), is(Map.of("claimMode", "rank", "deckSize", "32")));
    }

    @Test
    void givenSeatDescriptors_whenForViewer_thenViewCarriesThemAndBotSeatsGetDefaultsFilled() {
        List<OptionDescriptor> seatDescriptors = new TieredFactory().seatOptionDescriptors();
        SessionGame lobby = lobbyWith(1, 3);
        lobby.claimBot(null);
        SessionPlayer tuned = lobby.claimBot(null);
        lobby.setSeatOption(tuned.id(), "tier", "2");

        LobbyView view = LobbyView.forViewer(lobby, 2, 3, NO_OPTIONS, seatDescriptors, new PlayerId(0));

        assertThat(view.seatOptionDescriptors(), is(seatDescriptors));
        assertThat(view.players().get(0).seatOptions(), is(Map.of()));
        assertThat(view.players().get(1).seatOptions(), is(Map.of("tier", "1")));
        assertThat(view.players().get(2).seatOptions(), is(Map.of("tier", "2")));
    }

    @Test
    void givenGameWithoutSeatOptions_whenForViewer_thenBotSeatsCarryNone() {
        SessionGame lobby = lobbyWith(1, 3);
        lobby.claimBot(null);

        LobbyView view = LobbyView.forViewer(lobby, 2, 3, NO_OPTIONS, new PlayerId(0));

        assertThat(view.seatOptionDescriptors().isEmpty(), is(true));
        assertThat(view.players().get(1).seatOptions(), is(Map.of()));
    }
}
