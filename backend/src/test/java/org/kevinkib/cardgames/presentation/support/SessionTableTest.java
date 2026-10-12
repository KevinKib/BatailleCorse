package org.kevinkib.cardgames.presentation.support;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class SessionTableTest {

    private final SessionTable table = new SessionTable(new ThreeMenMorrisFactory());

    @Test
    void givenInstalledGame_whenGetGame_thenTheSessionServiceFindsIt() {
        ThreeMenMorris game = table.install(new ThreeMenMorris(GameId.generate()), ThreeMenMorrisFactory.GAME_TYPE);

        assertThat(table.sessions.getGame(game.getId(), ThreeMenMorris.class), is(sameInstance(game)));
        assertThat(table.sessions.gameType(game.getId()), is(ThreeMenMorrisFactory.GAME_TYPE));
    }

    @Test
    void givenInstalledGame_whenToken_thenEverySeatHasItsOwnResolvableToken() {
        ThreeMenMorris game = table.install(new ThreeMenMorris(GameId.generate()), ThreeMenMorrisFactory.GAME_TYPE);

        String first = table.token(game, 0);
        String second = table.token(game, 1);

        assertThat(first, is(not(second)));
        assertThat(table.sessions.findPlayerIdByToken(game.getId(), second).orElseThrow(), is(new PlayerId(1)));
    }

    @Test
    void givenTable_whenSendToToken_thenTheMessagingResolvesThroughItsSessions() {
        ThreeMenMorris game = table.install(new ThreeMenMorris(GameId.generate()), ThreeMenMorrisFactory.GAME_TYPE);

        table.messaging.sendToToken(game.getId(), table.token(game, 1),
                new org.kevinkib.cardgames.presentation.api.SuccessResponse("X", null, null, null));

        assertThat(table.messaging.seats, is(java.util.List.of(new PlayerId(1))));
    }
}
