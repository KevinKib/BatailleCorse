package org.kevinkib.cardgames.bullshit.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.CyclingSuitClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BotMemoryTest {

    private static final PlayerId ME = new PlayerId(0);
    private static final PlayerId OTHER = new PlayerId(1);
    private static final ClaimMode RANK_MODE = new AscendingRankClaimMode();

    private static Card card(FrenchRank rank, FrenchSuit suit) {
        return new Card(rank, suit);
    }

    @Test
    void givenNothingLearned_thenWholeDeckMinusOwnHandIsUnseen() {
        BotMemory memory = new BotMemory(ME);

        assertThat(memory.unseenPopulation(List.of(card(FrenchRank.ACE, FrenchSuit.HEART))), is(51));
        assertThat(memory.unseenPopulation(List.of()), is(52));
    }

    @Test
    void givenShortDeck_thenOnlyThirtyTwoCardsExistAndAbsentRanksAreNeverUnseen() {
        BotMemory memory = new BotMemory(ME, org.kevinkib.cardgames.bullshit.domain.deck.DeckSize.SHORT);

        assertThat(memory.unseenPopulation(List.of()), is(32));
        assertThat(memory.unseenPopulation(List.of(card(FrenchRank.ACE, FrenchSuit.HEART))), is(31));
    }

    @Test
    void givenOwnDiscard_thenThoseCardsAreKnownUntilThePileIsTaken() {
        BotMemory memory = new BotMemory(ME);
        memory.onOwnDiscard(List.of(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE)));

        assertThat(memory.knownPileCards().size(), is(2));
        assertThat(memory.unseenPopulation(List.of()), is(50));
        assertThat(memory.unseenMatching(List.of(), new RankTarget(FrenchRank.ACE), RANK_MODE), is(2));

        memory.onPileTaken();

        assertThat(memory.knownPileCards().size(), is(0));
        assertThat(memory.unseenPopulation(List.of()), is(52));
    }

    @Test
    void givenRevealForAnotherSeat_thenItsMatchesAreKnownForThatSeatOnly() {
        BotMemory memory = new BotMemory(ME);
        memory.onReveal(OTHER, List.of(card(FrenchRank.KING, FrenchSuit.HEART), card(FrenchRank.TWO, FrenchSuit.CLUB)));

        assertThat(memory.knownMatches(OTHER, new RankTarget(FrenchRank.KING), RANK_MODE), is(1));
        assertThat(memory.knownMatches(OTHER, new RankTarget(FrenchRank.THREE), RANK_MODE), is(0));
        assertThat(memory.knownMatches(new PlayerId(2), new RankTarget(FrenchRank.KING), RANK_MODE), is(0));
    }

    @Test
    void givenRevealedCardsOfTheBotItself_thenIgnored() {
        BotMemory memory = new BotMemory(ME);
        memory.onReveal(ME, List.of(card(FrenchRank.KING, FrenchSuit.HEART)));

        assertThat(memory.knownMatches(ME, new RankTarget(FrenchRank.KING), RANK_MODE), is(0));
    }

    @Test
    void givenSeatDiscardsAfterReveal_thenKnowledgeAboutItIsForgotten() {
        BotMemory memory = new BotMemory(ME);
        memory.onReveal(OTHER, List.of(card(FrenchRank.KING, FrenchSuit.HEART)));

        memory.onDiscardBy(OTHER);

        assertThat(memory.knownMatches(OTHER, new RankTarget(FrenchRank.KING), RANK_MODE), is(0));
    }

    @Test
    void givenTwoReveals_thenKnowledgeAccumulatesForTheSeat() {
        BotMemory memory = new BotMemory(ME);
        memory.onReveal(OTHER, List.of(card(FrenchRank.KING, FrenchSuit.HEART)));
        memory.onReveal(OTHER, List.of(card(FrenchRank.KING, FrenchSuit.SPADE)));

        assertThat(memory.knownMatches(OTHER, new RankTarget(FrenchRank.KING), RANK_MODE), is(2));
    }

    @Test
    void givenSuitMode_thenKnownMatchesAndUnseenMatchesCountSuits() {
        BotMemory memory = new BotMemory(ME);
        ClaimMode suitMode = new CyclingSuitClaimMode();
        memory.onReveal(OTHER, List.of(card(FrenchRank.KING, FrenchSuit.HEART), card(FrenchRank.TWO, FrenchSuit.HEART)));
        List<Card> hand = List.of(card(FrenchRank.ACE, FrenchSuit.HEART));

        assertThat(memory.knownMatches(OTHER, new SuitTarget(FrenchSuit.HEART), suitMode), is(2));
        assertThat(memory.unseenMatching(hand, new SuitTarget(FrenchSuit.HEART), suitMode), is(12));
    }

    @Test
    void givenRankMode_thenUnseenMatchingExcludesOwnHand() {
        BotMemory memory = new BotMemory(ME);
        List<Card> hand = List.of(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE));

        assertThat(memory.unseenMatching(hand, new RankTarget(FrenchRank.ACE), RANK_MODE), is(2));
        assertThat(memory.unseenMatching(hand, new RankTarget(FrenchRank.KING), RANK_MODE), is(4));
    }
}
