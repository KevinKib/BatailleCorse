package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.bataillecorse.core.domain.BatailleCorseBuilder.aBatailleCorse;
import static org.kevinkib.bataillecorse.core.domain.PlayerFixtures.createNumberOfPlayers;

class InMemorySessionRepositoryTest {

    @Nested
    class LoadSessionGameTest {

        @Test
        public void givenExistingSessionGame_whenLoadingSessionGame_thenReturnSessionGame() {
            var repository = new InMemorySessionRepository();
            var game = aBatailleCorse().withId(BatailleCorseId.generate()).withNbPlayers(2).buildAndInitialize();
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(game.getId(), players);

            repository.save(game, sessionGame);

            assertThat(repository.loadSessionGame(game.getId()), is(sessionGame));
        }
    }

    @Nested
    class LoadSessionTokenTest {

        @Test
        public void givenExistingSessionGame_whenLoadingSessionTokenByPlayerId_thenReturnToken() {
            var repository = new InMemorySessionRepository();
            var players = createNumberOfPlayers(2);
            var game = aBatailleCorse().withId(BatailleCorseId.generate()).withNbPlayers(2).buildAndInitialize();
            var sessionGame = SessionGame.create(game.getId(), players);
            SessionToken expectedToken = sessionGame.tokensByPlayer().get(new PlayerId(0));

            repository.save(game, sessionGame);

            assertThat(repository.loadSessionToken(game.getId(), new PlayerId(0)), is(expectedToken));
        }
    }
}
