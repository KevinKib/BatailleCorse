package org.kevinkib.cardgames.bullshit.domain.claim;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.bot.ScriptedRandom;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class CyclingSuitClaimModeTest {

    private final CyclingSuitClaimMode mode = new CyclingSuitClaimMode(new ScriptedRandom(0.0, 0.3, 0.6, 0.9));

    @Test
    void givenNewGame_whenInitial_thenHeart() {
        assertThat(mode.initial(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenHeart_whenNext_thenDiamond() {
        assertThat(mode.next(new SuitTarget(FrenchSuit.HEART)), is(new SuitTarget(FrenchSuit.DIAMOND)));
    }

    @Test
    void givenSpade_whenNext_thenWrapsToHeart() {
        assertThat(mode.next(new SuitTarget(FrenchSuit.SPADE)), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenAllCardsOfClaimedSuit_whenMatches_thenTrue() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.CLUB).build(),
                aCard().withRank(FrenchRank.KING).withSuit(FrenchSuit.CLUB).build());
        assertThat(mode.matches(cards, new SuitTarget(FrenchSuit.CLUB)), is(true));
    }

    @Test
    void givenAnyOffSuitCard_whenMatches_thenFalse() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.CLUB).build(),
                aCard().withRank(FrenchRank.KING).withSuit(FrenchSuit.SPADE).build());
        assertThat(mode.matches(cards, new SuitTarget(FrenchSuit.CLUB)), is(false));
    }

    @Test
    void givenRandomDraws_whenInitial_thenSuitFollowsTheDraw() {
        assertThat(new CyclingSuitClaimMode(new ScriptedRandom(0.0)).initial(), is(new SuitTarget(FrenchSuit.HEART)));
        assertThat(new CyclingSuitClaimMode(new ScriptedRandom(0.3)).initial(), is(new SuitTarget(FrenchSuit.DIAMOND)));
        assertThat(new CyclingSuitClaimMode(new ScriptedRandom(0.6)).initial(), is(new SuitTarget(FrenchSuit.CLUB)));
        assertThat(new CyclingSuitClaimMode(new ScriptedRandom(0.99)).initial(), is(new SuitTarget(FrenchSuit.SPADE)));
    }

    @Test
    void givenSameDrawTwice_whenNextRound_thenSuitRepeats() {
        CyclingSuitClaimMode drawing = new CyclingSuitClaimMode(new ScriptedRandom(0.6, 0.6));
        ClaimTarget first = drawing.initial();

        assertThat(drawing.nextRound(first), is(first));
    }

    @Test
    void givenRankMode_whenNextRound_thenTargetCarriesOn() {
        RankTarget target = new RankTarget(FrenchRank.FIVE);

        assertThat(new AscendingRankClaimMode().nextRound(target), is(target));
    }
}
