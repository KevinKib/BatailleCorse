package org.kevinkib.cardgames.sessionmanagement.session.domain;

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

            var sessionGame = SessionGame.create(GameId.generate(), players, "bataille-corse");

            assertThat(sessionGame.findTokenByPlayer(new PlayerId(0)).isPresent(), is(true));
            assertThat(sessionGame.findTokenByPlayer(new PlayerId(1)).isPresent(), is(true));
        }

        @Test
        public void givenPlayers_whenCreating_thenEachSeatHasADistinctToken() {
            var players = playerIds(2);

            var sessionGame = SessionGame.create(GameId.generate(), players, "bataille-corse");

            var token0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();
            var token1 = sessionGame.findTokenByPlayer(new PlayerId(1)).orElseThrow();
            assertThat(token0, is(not(equalTo(token1))));
        }

        @Test
        public void givenPlayers_whenCreating_thenSeatsAreOrderedById() {
            var players = playerIds(2);

            var sessionGame = SessionGame.create(GameId.generate(), players, "bataille-corse");

            var seats = sessionGame.seats();
            assertThat(seats, hasSize(2));
            assertThat(seats.get(0).id(), is(new PlayerId(0)));
            assertThat(seats.get(1).id(), is(new PlayerId(1)));
        }
    }

    @Nested
    class ClaimNextFreeSeatTest {

        @Test
        public void givenHostInSeatZero_whenClaimNextFreeSeat_thenLowestFreeSeatClaimed() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(6), "bullshit");
            sessionGame.claimSeat(new PlayerId(0), "Host");

            var claimed = sessionGame.claimNextFreeSeat("Bob");

            assertThat(claimed.id(), is(new PlayerId(1)));
            assertThat(claimed.name(), is("Bob"));
            assertThat(claimed.token(), is(notNullValue()));
        }

        @Test
        public void givenAllSeatsClaimed_whenClaimNextFreeSeat_thenThrowsRoomFull() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(2), "bullshit");
            sessionGame.claimSeat(new PlayerId(0), "Host");
            sessionGame.claimSeat(new PlayerId(1), "Bob");

            org.junit.jupiter.api.Assertions.assertThrows(
                    RoomFullException.class, () -> sessionGame.claimNextFreeSeat("Late"));
        }

        @Test
        public void givenBlankName_whenClaimNextFreeSeat_thenSeatGetsDefaultName() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(2), "bullshit");

            var claimed = sessionGame.claimNextFreeSeat("  ");

            assertThat(claimed.name(), is("Player 1"));
        }
    }

    @Nested
    class ClaimSeatTest {

        @Test
        public void givenNewSessionGame_whenCreated_thenNoSeatsAreClaimed() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(2), "bullshit");

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(false));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
        }

        @Test
        public void givenSeat_whenClaimSeatWithName_thenIsClaimedWithThatName() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(2), "bullshit");

            sessionGame.claimSeat(new PlayerId(0), "Alice");

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(true));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
            assertThat(sessionGame.seats().get(0).name(), is("Alice"));
        }

        @Test
        public void givenClaimedSeat_whenClaimSeatAgain_thenThrowsSeatUnavailable() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(2), "bullshit");
            sessionGame.claimSeat(new PlayerId(1), "Bob");

            org.junit.jupiter.api.Assertions.assertThrows(
                    SeatUnavailableException.class, () -> sessionGame.claimSeat(new PlayerId(1), "Carol"));
        }

        @Test
        public void givenSeats_whenClaimAllSeats_thenEverySeatClaimedAndCounted() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(3), "bullshit");

            sessionGame.claimAllSeats();

            assertThat(sessionGame.claimedCount(), is(3));
            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(true));
            assertThat(sessionGame.isClaimed(new PlayerId(2)), is(true));
        }

        @Test
        public void givenSomeClaimed_whenClaimedCount_thenReflectsClaims() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(4), "bullshit");
            sessionGame.claimSeat(new PlayerId(0), "Host");
            sessionGame.claimSeat(new PlayerId(1), "Bob");

            assertThat(sessionGame.claimedCount(), is(2));
        }

        @Test
        public void givenHostSeat_whenIsHost_thenOnlySeatZeroIsHost() {
            var sessionGame = SessionGame.create(GameId.generate(), playerIds(2), "bullshit");

            assertThat(sessionGame.isHost(new PlayerId(0)), is(true));
            assertThat(sessionGame.isHost(new PlayerId(1)), is(false));
        }
    }

    @Nested
    class FindPlayerByTokenTest {

        @Test
        public void givenSessionGame_withToken_whenLookingUp_thenReturnPlayerId() {
            var players = playerIds(2);
            var sessionGame = SessionGame.create(GameId.generate(), players, "bataille-corse");
            var tokenForPlayer0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();

            Optional<PlayerId> result = sessionGame.findPlayerByToken(tokenForPlayer0);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        public void givenSessionGame_withUnknownToken_whenLookingUp_thenReturnEmpty() {
            var players = playerIds(2);
            var sessionGame = SessionGame.create(GameId.generate(), players, "bataille-corse");

            Optional<PlayerId> result = sessionGame.findPlayerByToken(SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }

    @Nested
    class RematchTest {

        private SessionGame newSession() {
            return SessionGame.create(GameId.generate(), playerIds(2), "bataille-corse");
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
