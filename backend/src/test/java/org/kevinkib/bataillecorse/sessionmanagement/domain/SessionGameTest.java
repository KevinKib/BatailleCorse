package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.Map;
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

            assertThat(sessionGame.playersByToken().values(), hasSize(2));
            assertThat(sessionGame.playersByToken().values(), hasItems(new PlayerId(0), new PlayerId(1)));
        }

        @Test
        public void givenPlayers_whenCreating_thenEachPlayerHasADistinctToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            assertThat(sessionGame.playersByToken().keySet(), hasSize(2));
        }
    }

    @Nested
    class FindPlayerByTokenTest {

        @Test
        public void givenSessionGame_withSessionId_whenLoadingBySessionId_thenReturnPlayerId() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);
            var tokenForPlayer0 = sessionGame.playersByToken().entrySet().stream()
                    .filter(e -> e.getValue().equals(new PlayerId(0)))
                    .map(Map.Entry::getKey)
                    .findFirst().orElseThrow();

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
