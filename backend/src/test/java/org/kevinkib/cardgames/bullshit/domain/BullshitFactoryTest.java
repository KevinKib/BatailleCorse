package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BullshitFactoryTest {

    @Test
    void givenId_whenCreate_thenReturnsPlayableBullshitWithThatId() {
        GameId id = GameId.generate();

        Game game = new BullshitFactory().create(id, 3);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(3));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void gameType_isBullshit() {
        assertThat(new BullshitFactory().gameType(), is("bullshit"));
    }

    @Test
    void givenSuitOption_whenCreate_thenInitialTargetIsHeart() {
        Bullshit game = (Bullshit) new BullshitFactory()
                .create(GameId.generate(), 3, GameOptions.of(Map.of("claimMode", "suit")));

        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenNoOptions_whenCreate_thenInitialTargetIsAce() {
        Bullshit game = (Bullshit) new BullshitFactory().create(GameId.generate(), 3);

        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
    }
}
