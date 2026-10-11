package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class SessionGameTokenHygieneTest {

    private SessionGame lobby;

    @BeforeEach
    void setUp() {
        lobby = SessionGame.create(GameId.generate(), 4, "bullshit");
        lobby.claimHost("Alice");
    }

    @Test
    void givenAFreeSeat_whenLookingUpItsToken_thenNothingResolves() {
        SessionToken freeSeatToken = lobby.players().get(new PlayerId(1)).token();

        assertThat(lobby.findPlayerByToken(freeSeatToken).isPresent(), is(false));
        assertThat(lobby.findTokenByPlayer(new PlayerId(1)).isPresent(), is(false));
    }

    @Test
    void givenAClaimedSeat_whenLookingUpItsToken_thenItResolves() {
        SessionPlayer bob = lobby.claimNextFreeSeat("Bob");

        assertThat(lobby.findPlayerByToken(bob.token()).get(), is(new PlayerId(1)));
        assertThat(lobby.findTokenByPlayer(new PlayerId(1)).get(), is(bob.token()));
    }

    @Test
    void givenABotRemoved_whenANewcomerTakesTheSeat_thenItsTokenDiffersFromTheBotOne() {
        SessionToken botToken = lobby.claimBot(null).token();
        lobby.removeBot(new PlayerId(1));

        SessionPlayer newcomer = lobby.claimNextFreeSeat("Bob");

        assertThat(newcomer.token(), is(not(botToken)));
        assertThat(lobby.findPlayerByToken(botToken).isPresent(), is(false));
    }

    @Test
    void givenADepartedSeatReleasedForARematch_whenANewcomerClaimsIt_thenTheTokenIsFresh() {
        SessionPlayer bob = lobby.claimNextFreeSeat("Bob");
        SessionToken oldToken = bob.token();
        lobby.leaveRematch(new PlayerId(1));
        lobby.releaseDepartedSeats();

        SessionPlayer newcomer = lobby.claimNextFreeSeat("Carol");

        assertThat(newcomer.token(), is(not(oldToken)));
        assertThat(lobby.findPlayerByToken(oldToken).isPresent(), is(false));
    }
}
