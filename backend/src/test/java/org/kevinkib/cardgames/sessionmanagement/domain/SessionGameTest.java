package org.kevinkib.cardgames.sessionmanagement.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SessionGameTest {

    private static List<PlayerId> playerIds(int count) {
        return IntStream.range(0, count).mapToObj(PlayerId::new).toList();
    }

    @Nested
    class CreateTest {

        @Test
        public void givenPlayers_whenCreating_thenEachSeatHasAToken() {
            var players = playerIds(2);

            var sessionGame = SessionGame.create(GameId.generate(), players);

            assertThat(sessionGame.findTokenByPlayer(new PlayerId(0)).isPresent(), is(true));
            assertThat(sessionGame.findTokenByPlayer(new PlayerId(1)).isPresent(), is(true));
        }

        @Test
        public void givenPlayers_whenCreating_thenEachSeatHasADistinctToken() {
            var players = playerIds(2);

            var sessionGame = SessionGame.create(GameId.generate(), players);

            var token0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();
            var token1 = sessionGame.findTokenByPlayer(new PlayerId(1)).orElseThrow();
            assertThat(token0, is(not(equalTo(token1))));
        }

        @Test
        public void givenPlayers_whenCreating_thenSeatsAreOrderedById() {
            var players = playerIds(2);

            var sessionGame = SessionGame.create(GameId.generate(), players);

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
            var players = playerIds(2);

            var sessionGame = SessionGame.create(GameId.generate(), players);

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(false));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
        }

        @Test
        public void givenSeat_whenClaimedWithName_thenIsClaimedWithThatName() {
            var players = playerIds(2);
            var sessionGame = SessionGame.create(GameId.generate(), players);

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
            var players = playerIds(2);
            var sessionGame = SessionGame.create(GameId.generate(), players);
            var tokenForPlayer0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();

            Optional<PlayerId> result = sessionGame.findPlayerByToken(tokenForPlayer0);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        public void givenSessionGame_withUnknownToken_whenLookingUp_thenReturnEmpty() {
            var players = playerIds(2);
            var sessionGame = SessionGame.create(GameId.generate(), players);

            Optional<PlayerId> result = sessionGame.findPlayerByToken(SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }

    @Nested
    class RematchTest {

        private SessionGame newSession() {
            return SessionGame.create(GameId.generate(), playerIds(2));
        }

        @Test
        void givenNoSeatRequested_whenIsRematchUnanimous_thenFalse() {
            var session = newSession();

            assertThat(session.isRematchUnanimous(), is(false));
        }

        @Test
        void givenOneOfTwoSeatsRequested_whenIsRematchUnanimous_thenFalse() {
            var session = newSession();

            session.requestRematch(new PlayerId(0));

            assertThat(session.isRematchUnanimous(), is(false));
        }

        @Test
        void givenAllSeatsRequested_whenIsRematchUnanimous_thenTrue() {
            var session = newSession();

            session.requestRematch(new PlayerId(0));
            session.requestRematch(new PlayerId(1));

            assertThat(session.isRematchUnanimous(), is(true));
        }

        @Test
        void givenUnanimousRematch_whenClearRematch_thenNoLongerUnanimous() {
            var session = newSession();
            session.requestRematch(new PlayerId(0));
            session.requestRematch(new PlayerId(1));

            session.clearRematch();

            assertThat(session.isRematchUnanimous(), is(false));
        }
    }
}
