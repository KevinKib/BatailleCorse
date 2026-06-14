package org.kevinkib.cardgames.bullshit.domain;
import org.kevinkib.cardgames.game.GameId;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.claim.CyclingSuitClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.bullshit.domain.player.PlayerId;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;

class BullshitTest {

    @Test
    void givenFreshGame_thenWholeDeckDealtAndAceClaimedFirst() {
        Bullshit game = new Bullshit(GameId.generate(), 4);

        int totalCards = game.getPlayers().stream().mapToInt(Player::handSize).sum();
        assertThat(totalCards, is(52));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
        assertThat(game.getCurrentPlayerIndex(), is(0));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void givenThreePlayers_thenDealtUnevenlyWithoutError() {
        Bullshit game = new Bullshit(GameId.generate(), 3);
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

    @Test
    void givenValidDiscard_thenCardsLeaveHandAndPileGrowsAndTurnAdvances() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        Player p0 = game.getPlayers().get(0);

        game.discard(new PlayerId(0), p0.getCards());

        assertThat(game.getDiscardPileSize(), is(2));
        assertThat(game.getCurrentPlayerIndex(), is(1));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.TWO)));
        assertThat(game.getLastDiscard().isPresent(), is(true));
    }

    @Test
    void givenNotYourTurn_whenDiscard_thenThrows() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        assertThrows(NotPlayersTurnException.class,
                () -> game.discard(new PlayerId(1), game.getPlayers().get(1).getCards()));
    }

    @Test
    void givenFiveCards_whenDiscard_thenInvalidCount() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.KING),
                        playerWithRanks(1, FrenchRank.TWO))
                .build();
        assertThrows(InvalidDiscardCountException.class,
                () -> game.discard(new PlayerId(0), game.getPlayers().get(0).getCards()));
    }

    @Test
    void givenCardsNotHeld_whenDiscard_thenThrows() {
        Player p0 = playerWithRanks(0, FrenchRank.ACE);
        Player p1 = playerWithRanks(1, FrenchRank.TWO);
        Bullshit game = BullshitBuilder.aBullshit().withPlayers(p0, p1).build();

        assertThrows(CardsNotInHandException.class,
                () -> game.discard(new PlayerId(0), p1.getCards()));
    }

    @Test
    void givenLie_whenCalled_thenLiarTakesPileAndRoundResets() throws Exception {
        // p0 must claim ACE but actually holds a KING -> a lie.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());

        CallBullshitOutcome outcome = game.callBullshit(new PlayerId(1));

        assertThat(outcome.claimWasTruthful(), is(false));
        assertThat(outcome.pilePicker(), is(new PlayerId(0)));
        assertThat(game.getDiscardPileSize(), is(0));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
        assertThat(game.getCurrentPlayerIndex(), is(0));            // liar starts next round
        assertThat(game.getPlayers().get(0).handSize(), is(1));     // took the pile back
    }

    @Test
    void givenTruthfulClaim_whenCalled_thenCallerTakesPile() throws Exception {
        // p0 keeps a KING so it does NOT empty its hand (avoids the win branch);
        // it discards only the ACE, truthfully claiming ACE.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));

        CallBullshitOutcome outcome = game.callBullshit(new PlayerId(1));

        assertThat(outcome.claimWasTruthful(), is(true));
        assertThat(outcome.pilePicker(), is(new PlayerId(1)));
        assertThat(game.isFinished(), is(false));
        assertThat(game.getCurrentPlayerIndex(), is(1));            // caller starts next round
        assertThat(game.getPlayers().get(1).handSize(), is(2));     // had 1, took the 1-card pile
    }

    @Test
    void givenTruthfulFinalDiscard_whenCalled_thenClaimantWins() throws Exception {
        // p0's only card is an ACE, claimed as ACE -> empties hand truthfully.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());

        game.callBullshit(new PlayerId(1));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
    }

    @Test
    void givenOwnDiscard_whenCallBullshit_thenThrows() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));

        assertThrows(CannotCallBullshitException.class,
                () -> game.callBullshit(new PlayerId(0)));
    }

    @Test
    void givenNoDiscardYet_whenCallBullshit_thenThrows() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        assertThrows(CannotCallBullshitException.class,
                () -> game.callBullshit(new PlayerId(1)));
    }

    @Test
    void givenEmptyHandThenNextPlayerPlaysOn_thenBluffStandsAndWins() throws Exception {
        // p0 empties hand (pendingWinner); p1 declines to call by discarding -> p0 wins.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());
        assertThat(game.getPendingWinner().isPresent(), is(true));

        game.discard(new PlayerId(1), game.getPlayers().get(1).getCards());

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
    }

    @Test
    void givenThreePlayers_whenOneForfeits_thenRemovedFromRotation() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO), playerWithRanks(2, FrenchRank.THREE))
                .build();

        game.forfeit(new PlayerId(1));

        assertThat(game.getPlayers(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void givenTwoPlayers_whenOneForfeits_thenOtherWins() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();

        game.forfeit(new PlayerId(0));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }

    @Test
    void givenFinishedGame_whenForfeit_thenNoOp() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        game.forfeit(new PlayerId(0)); // p1 wins
        Player winnerBefore = game.getWinner();

        game.forfeit(new PlayerId(1)); // already finished -> ignored

        assertThat(game.getWinner(), is(winnerBefore));
    }

    @Test
    void givenSuitClaimMode_thenForcedClaimsCycleBySuit() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withClaimMode(new CyclingSuitClaimMode())
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .build();
        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));

        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));

        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.DIAMOND)));
    }

    @Test
    void givenForfeitOfPlayerBeforeCurrent_thenCurrentPlayersTurnPreserved() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE),
                        playerWithRanks(1, FrenchRank.TWO),
                        playerWithRanks(2, FrenchRank.THREE))
                .withCurrentPlayerIndex(2)
                .build();

        game.forfeit(new PlayerId(0)); // removed before the current player

        assertThat(game.getCurrentPlayer().id(), is(new PlayerId(2)));
    }

    @Test
    void givenForfeitOfCurrentPlayer_thenTurnPassesToNext() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE),
                        playerWithRanks(1, FrenchRank.TWO),
                        playerWithRanks(2, FrenchRank.THREE))
                .withCurrentPlayerIndex(1)
                .build();

        game.forfeit(new PlayerId(1)); // the current player leaves

        assertThat(game.getCurrentPlayer().id(), is(new PlayerId(2)));
    }

    @Test
    void givenThreePlayersTruthfulCall_thenCallerStartsNextRoundAtAce() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING),
                        playerWithRanks(1, FrenchRank.TWO),
                        playerWithRanks(2, FrenchRank.THREE))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1)); // truthful ACE

        game.callBullshit(new PlayerId(2)); // truthful -> p2 takes pile and starts

        assertThat(game.getCurrentPlayer().id(), is(new PlayerId(2)));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
    }

    @Test
    void givenThreePlayersLieCalled_thenLiarStartsNextRoundAndPendingWinnerCleared() throws Exception {
        // p1 (current) holds only a KING but the forced claim is ACE -> a lie that empties its hand.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE),
                        playerWithRanks(1, FrenchRank.KING),
                        playerWithRanks(2, FrenchRank.THREE))
                .withCurrentTarget(FrenchRank.ACE)
                .withCurrentPlayerIndex(1)
                .build();
        game.discard(new PlayerId(1), game.getPlayers().get(1).getCards()); // lie, p1 now empty -> pending
        assertThat(game.getPendingWinner().isPresent(), is(true));

        game.callBullshit(new PlayerId(2)); // lie exposed -> p1 takes pile back, no longer pending

        assertThat(game.getCurrentPlayer().id(), is(new PlayerId(1))); // liar starts next round
        assertThat(game.getPendingWinner().isEmpty(), is(true));
        assertThat(game.isFinished(), is(false));
    }
}
