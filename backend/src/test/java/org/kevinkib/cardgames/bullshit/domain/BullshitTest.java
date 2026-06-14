package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;

class BullshitTest {

    @Test
    void givenFreshGame_thenWholeDeckDealtAndAceClaimedFirst() {
        Bullshit game = new Bullshit(BullshitId.generate(), 4);

        int totalCards = game.getPlayers().stream().mapToInt(Player::handSize).sum();
        assertThat(totalCards, is(52));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
        assertThat(game.getCurrentPlayerIndex(), is(0));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void givenThreePlayers_thenDealtUnevenlyWithoutError() {
        Bullshit game = new Bullshit(BullshitId.generate(), 3);
        int totalCards = game.getPlayers().stream().mapToInt(Player::handSize).sum();
        assertThat(totalCards, is(52));
        assertThat(game.getPlayers(), hasSize(3));
    }

    @Test
    void givenNoDiscardYet_whenCurrentPlayerActions_thenOnlyDiscard() {
        Bullshit game = aThreePlayerGame();
        assertThat(game.getAvailableActions(new PlayerId(0)), contains(Action.DISCARD));
    }

    @Test
    void givenNoDiscardYet_whenOtherPlayerActions_thenNone() {
        Bullshit game = aThreePlayerGame();
        assertThat(game.getAvailableActions(new PlayerId(1)), hasSize(0));
    }

    private Bullshit aThreePlayerGame() {
        return BullshitBuilder.aBullshit()
                .withPlayers(
                        playerWithRanks(0, FrenchRank.ACE),
                        playerWithRanks(1, FrenchRank.TWO),
                        playerWithRanks(2, FrenchRank.THREE))
                .build();
    }
}
