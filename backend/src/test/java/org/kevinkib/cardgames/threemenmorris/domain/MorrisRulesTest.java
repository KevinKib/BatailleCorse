package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules.FlyingRule;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules.LineRule;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules.RemovalRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The rule data of the line, removal, flying and quiet-move rules, and the types of the Six contract. */
class MorrisRulesTest {

    @Test
    void tapatanAndAchiAreWinByLineRulesWithNoFlyingAndNoQuietLimit() {
        for (MorrisRules rules : new MorrisRules[] {MorrisRules.TAPATAN, MorrisRules.ACHI}) {
            assertThat(rules.lineRule(), is(LineRule.WIN));
            assertThat(rules.removal(), is(RemovalRule.PROTECT_MILLS));
            assertThat(rules.flying(), is(FlyingRule.NEVER));
            assertThat(rules.quietPlyLimit(), is(0));
            assertThat(rules.solvable(), is(true));
        }
        assertThat(MorrisRules.TAPATAN, is(new MorrisRules(Geometry.TAPATAN, 3, LineRule.WIN,
                RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 0)));
        assertThat(MorrisRules.ACHI.piecesPerPlayer(), is(4));
    }

    @Test
    void sixMenIsSixPiecesMillsProtectedNoFlyingAndFiftyQuietPlies() {
        assertThat(MorrisRules.SIX_MEN, is(new MorrisRules(Geometry.SIX_MEN, 6, LineRule.MILL,
                RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 50)));
        assertThat(MorrisRules.SIX_MEN.geometry().points(), is(16));
    }

    @Test
    void onlyWinByLineRulesAreSolvable() {
        assertThat(MorrisRules.SIX_MEN.solvable(), is(false));
        assertThat(new MorrisRules(Geometry.TAPATAN, 3, LineRule.MILL, RemovalRule.PROTECT_MILLS,
                FlyingRule.NEVER, 0).solvable(), is(false));
        assertThat(new MorrisRules(Geometry.TAPATAN, 3, LineRule.WIN, RemovalRule.PROTECT_MILLS,
                FlyingRule.AT_THREE, 0).solvable(), is(true));
    }

    @Test
    void theConvenienceConstructorBuildsAWinRule() {
        MorrisRules rules = new MorrisRules(Geometry.TAPATAN, 5);

        assertThat(rules.lineRule(), is(LineRule.WIN));
        assertThat(rules.flying(), is(FlyingRule.NEVER));
        assertThat(rules.quietPlyLimit(), is(0));
    }

    @Test
    void incoherentRulesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new MorrisRules(Geometry.SIX_MEN, 2,
                LineRule.MILL, RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 50));
        assertThrows(IllegalArgumentException.class, () -> new MorrisRules(Geometry.SIX_MEN, 6,
                LineRule.MILL, RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, -1));
        assertThrows(IllegalArgumentException.class, () -> new MorrisRules(null, 6,
                LineRule.MILL, RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 0));
        assertThrows(IllegalArgumentException.class, () -> new MorrisRules(Geometry.SIX_MEN, 6,
                null, RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 0));
        assertThrows(IllegalArgumentException.class, () -> new MorrisRules(Geometry.SIX_MEN, 6,
                LineRule.MILL, null, FlyingRule.NEVER, 0));
        assertThrows(IllegalArgumentException.class, () -> new MorrisRules(Geometry.SIX_MEN, 6,
                LineRule.MILL, RemovalRule.PROTECT_MILLS, null, 0));
    }

    @Test
    void theEndReasonsOfSixAreDeclaredAndDrawOnlyForTheMoveLimit() {
        assertThat(new MorrisResult(null, MorrisResult.Reason.DRAW_MOVE_LIMIT).isDraw(), is(true));
        assertThat(new MorrisResult(null, MorrisResult.Reason.DRAW_MOVE_LIMIT).isFinished(), is(true));
        assertThat(new MorrisResult(new PlayerId(0), MorrisResult.Reason.REDUCED).isDraw(), is(false));
        assertThat(new MorrisResult(new PlayerId(0), MorrisResult.Reason.REDUCED).isFinished(), is(true));
    }

    @Test
    void theRemoveActionIsDeclared() {
        assertThat(Action.valueOf("REMOVE"), is(Action.REMOVE));
    }

    @Test
    void theAggregateAcceptsMillRules() {
        assertThat(new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, new PlayerId(0)).rules(),
                is(MorrisRules.SIX_MEN));
    }
}
