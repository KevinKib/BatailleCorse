package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.empty;

class ThreeMenMorrisForfeitAndFactoryTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    @Test
    void forfeitingGivesTheWinToTheOtherPlayer() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        game.forfeit(WHITE);

        assertThat(game.isFinished(), is(true));
        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new MorrisResult(BLACK, MorrisResult.Reason.FORFEIT)));
        assertThat(game.version(), is(1));
        assertThat(game.getPlayerIds(), is(java.util.List.of(WHITE, BLACK)));
    }

    @Test
    void forfeitingTwiceOrAfterTheEndOrByAnUnknownSeatChangesNothing() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        game.forfeit(new PlayerId(7));
        assertThat(game.isFinished(), is(false));
        assertThat(game.version(), is(0));

        game.forfeit(BLACK);
        game.forfeit(WHITE);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.FORFEIT)));
        assertThat(game.version(), is(1));
    }

    @Test
    void anOngoingGameHasNoResultAndNoWinningLine() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

        assertThat(game.result().isFinished(), is(false));
        assertThat(game.winningLine(), is(empty()));
    }

    @Test
    void theFactoryBuildsATwoPlayerGameWithoutOptionsOrSeatOptions() {
        ThreeMenMorrisFactory factory = new ThreeMenMorrisFactory();
        GameId id = GameId.generate();

        Game game = factory.create(id, 2);

        assertThat(factory.gameType(), is("three-men-morris"));
        assertThat(factory.minPlayers(), is(2));
        assertThat(factory.maxPlayers(), is(2));
        assertThat(game, instanceOf(ThreeMenMorris.class));
        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), is(java.util.List.of(WHITE, BLACK)));
        assertThat(factory.optionDescriptors(), is(empty()));
        assertThat(factory.seatOptionDescriptors(), is(empty()));
        assertThat(factory.validate(GameOptions.none()), is(GameOptions.none()));
        assertThat(factory.create(id, 2, GameOptions.none()), instanceOf(ThreeMenMorris.class));
    }
}
