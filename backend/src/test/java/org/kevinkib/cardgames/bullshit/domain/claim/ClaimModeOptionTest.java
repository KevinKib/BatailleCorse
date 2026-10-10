package org.kevinkib.cardgames.bullshit.domain.claim;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.bot.ScriptedRandom;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ClaimModeOptionTest {

    @Test
    void givenRankKey_whenFromKey_thenRank() {
        assertThat(ClaimModeOption.fromKey("rank"), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuitKey_whenFromKey_thenSuit() {
        assertThat(ClaimModeOption.fromKey("suit"), is(ClaimModeOption.SUIT));
    }

    @Test
    void givenNull_whenFromKey_thenRankDefault() {
        assertThat(ClaimModeOption.fromKey(null), is(ClaimModeOption.RANK));
    }

    @Test
    void givenUnknownKey_whenFromKey_thenRankDefault() {
        assertThat(ClaimModeOption.fromKey("bogus"), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuit_whenCreate_thenInitialTargetIsDrawn() {
        assertThat(ClaimModeOption.SUIT.create(DeckSize.FULL, new ScriptedRandom(0.0)).initial(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenRank_whenCreate_thenInitialTargetIsAce() {
        assertThat(ClaimModeOption.RANK.create(DeckSize.FULL, new ScriptedRandom()).initial(), is(new RankTarget(FrenchRank.ACE)));
    }
}
