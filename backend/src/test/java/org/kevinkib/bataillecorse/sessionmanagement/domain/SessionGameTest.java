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
        public void givenPlayers_whenCreating_thenEachSeatHasAToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            assertThat(sessionGame.findTokenByPlayer(new PlayerId(0)).isPresent(), is(true));
            assertThat(sessionGame.findTokenByPlayer(new PlayerId(1)).isPresent(), is(true));
        }

        @Test
        public void givenPlayers_whenCreating_thenEachSeatHasADistinctToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            var token0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();
            var token1 = sessionGame.findTokenByPlayer(new PlayerId(1)).orElseThrow();
            assertThat(token0, is(not(equalTo(token1))));
        }

        @Test
        public void givenPlayers_whenCreating_thenSeatsAreOrderedById() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            var seats = sessionGame.seats();
            assertThat(seats, hasSize(2));
            assertThat(seats.get(0).id(), is(new PlayerId(0)));
            assertThat(seats.get(1).id(), is(new PlayerId(1)));
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
        public void givenSeat_whenClaimedWithName_thenIsClaimedWithThatName() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            sessionGame.claim(new PlayerId(0), "Alice");

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(true));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
            assertThat(sessionGame.seats().get(0).name(), is("Alice"));
        }
    }

    @Nested
    class FindPlayerByTokenTest {

        @Test
        public void givenSessionGame_withToken_whenLookingUp_thenReturnPlayerId() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);
            var tokenForPlayer0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();

            Optional<PlayerId> result = sessionGame.findPlayerByToken(tokenForPlayer0);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        public void givenSessionGame_withUnknownToken_whenLookingUp_thenReturnEmpty() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            Optional<PlayerId> result = sessionGame.findPlayerByToken(SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }
}
