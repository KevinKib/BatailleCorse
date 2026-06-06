package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.kevinkib.bataillecorse.core.domain.PlayerFixtures.createNumberOfPlayers;

class SessionGameTest {

    @Nested
    class CreateTest {

        @Test
        public void givenPlayers_whenCreating_thenEachPlayerHasAToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            assertThat(sessionGame.tokensByPlayer().keySet(), hasSize(2));
            assertThat(sessionGame.tokensByPlayer(), hasKey(new PlayerId(0)));
            assertThat(sessionGame.tokensByPlayer(), hasKey(new PlayerId(1)));
        }

        @Test
        public void givenPlayers_whenCreating_thenEachPlayerHasADistinctToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            var token0 = sessionGame.tokensByPlayer().get(new PlayerId(0));
            var token1 = sessionGame.tokensByPlayer().get(new PlayerId(1));
            assertThat(token0, is(not(equalTo(token1))));
        }
    }

    @Nested
    class ClaimTest {

        @Test
        public void givenNewSessionGame_whenCreated_thenNoSeatsAreClaimed() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(false));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
        }

        @Test
        public void givenSeat_whenClaimed_thenIsClaimed() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            sessionGame.claim(new PlayerId(0));

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(true));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
        }
    }

    @Nested
    class FindPlayerByTokenTest {

        @Test
        public void givenSessionGame_withSessionId_whenLoadingBySessionId_thenReturnPlayerId() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);
            var tokenForPlayer0 = sessionGame.tokensByPlayer().get(new PlayerId(0));

            Optional<PlayerId> result = sessionGame.findPlayerByToken(tokenForPlayer0);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        public void givenSessionGame_withUnknownSessionId_whenLoadingBySessionId_thenReturnEmpty() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            Optional<PlayerId> result = sessionGame.findPlayerByToken(SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }
}
