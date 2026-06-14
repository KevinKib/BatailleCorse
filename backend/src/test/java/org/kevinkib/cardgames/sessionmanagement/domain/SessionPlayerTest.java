package org.kevinkib.cardgames.sessionmanagement.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SessionPlayerTest {

    @Test
    public void givenNewSessionPlayer_thenUnclaimedWithNoName() {
        SessionPlayer player = new SessionPlayer(new PlayerId(0), SessionToken.generate());

        assertThat(player.isClaimed(), is(false));
        assertThat(player.name(), is(nullValue()));
        assertThat(player.token(), is(notNullValue()));
    }

    @Test
    public void givenSessionPlayer_whenClaimed_thenClaimedWithName() {
        SessionPlayer player = new SessionPlayer(new PlayerId(0), SessionToken.generate());

        player.claim("Alice");

        assertThat(player.isClaimed(), is(true));
        assertThat(player.name(), is("Alice"));
    }
}
