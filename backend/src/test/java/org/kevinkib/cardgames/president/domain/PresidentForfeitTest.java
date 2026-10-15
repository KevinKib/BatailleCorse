package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.event.PresidentResult;
import org.kevinkib.cardgames.president.domain.round.FinishedGameException;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/** Spec section 8: one test per phase and per position of the player who forfeits. */
class PresidentForfeitTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    private static President fourPlayers() {
        return aPresident().withHand("3C 9C").withHand("4C 10C").withHand("5C JC").withHand("6C QC")
                .leader(0).build();
    }

    private static President threePlayers() {
        return aPresident().withHand("3C 4C").withHand("5C 9C").withHand("6C JC").leader(0).build();
    }

    // ----- PLAYING -----

    @Test
    void theLeaderForfeitsBeforeLeadingAndTheNextSeatLeads() throws Exception {
        President game = fourPlayers();
        int version = game.version();

        game.forfeit(p(0));

        assertThat(game.version(), is(version + 1));
        assertThat(game.hand(p(0)), is(empty()));
        assertThat(game.discard(), containsInAnyOrder(card("3C"), card("9C")));
        assertThat(game.currentSeat(), is(p(1)));
        assertThat(game.trick().leader(), is(p(1)));
        assertThat(game.getPlayerIds(), contains(p(1), p(2), p(3)));
        assertThat(game.finishingOrder(), is(empty()));
        assertThat(game.phase(), is(Phase.PLAYING));
    }

    @Test
    void theCurrentPlayerForfeitsInTheMiddleOfATrickAndTheTurnMovesOn() throws Exception {
        President game = fourPlayers();
        game.play(p(0), cards("9C"));

        game.forfeit(p(1));

        assertThat(game.currentSeat(), is(p(2)));
        assertThat(game.trick().top().cards(), is(cards("9C")));
        game.play(p(2), cards("JC"));
        game.pass(p(3));
        assertThat(game.currentSeat(), is(p(0)));
    }

    @Test
    void thePlayerWhoOwnsTheTopOfTheTrickForfeitsAndTheTrickGoesOnToTheNextSeatWithCards() throws Exception {
        President game = fourPlayers();
        game.play(p(0), cards("9C"));
        game.pass(p(1));
        game.pass(p(2));

        game.forfeit(p(0));

        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.trick().top().cards(), is(cards("9C")));
        game.pass(p(3));
        // everyone answered: the trick is won as if the forfeiter had gone out, so the next seat leads
        assertThat(game.currentSeat(), is(p(1)));
        assertThat(game.trick().plays(), is(empty()));
        assertThat(game.discard(), containsInAnyOrder(card("9C"), card("3C")));
        assertThat(game.finishingOrder(), is(empty()));
    }

    @Test
    void aWaitingPlayerForfeitsAndTheTrickEndsAfterTheLastAnswer() throws Exception {
        President game = fourPlayers();
        game.play(p(0), cards("9C"));
        game.pass(p(1));

        game.forfeit(p(3));

        assertThat(game.currentSeat(), is(p(2)));
        game.pass(p(2));
        assertThat(game.currentSeat(), is(p(0)));
        assertThat(game.trick().plays(), is(empty()));
    }

    @Test
    void whenEveryoneLeftToAnswerForfeitsTheTrickIsWonByItsLastPlayer() throws Exception {
        President game = fourPlayers();
        game.play(p(0), cards("9C"));
        game.pass(p(1));

        game.forfeit(p(3));
        game.forfeit(p(2));

        assertThat(game.currentSeat(), is(p(0)));
        assertThat(game.trick().plays(), is(empty()));
        assertThat(game.phase(), is(Phase.PLAYING));
    }

    @Test
    void aPlayerWhoAlreadyWentOutKeepsTheirPlaceButTakesNoPoints() throws Exception {
        President game = aPresident().withHand("3C").withHand("4C").withHand("5C").withHand("6C").leader(0).build();
        game.play(p(0), cards("3C"));

        game.forfeit(p(0));

        assertThat(game.phase(), is(Phase.PLAYING));
        game.play(p(1), cards("4C"));
        game.play(p(2), cards("5C"));
        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.lastRoundResult().finishingOrder(), contains(p(1), p(2), p(3)));
        assertThat(game.lastRoundResult().points(), is(Map.of(p(1), 2, p(2), 1, p(3), 0)));
        assertThat(game.lastRoundResult().titles(),
                is(Map.of(p(1), Title.PRESIDENT, p(2), Title.NEUTRAL, p(3), Title.SCUM)));
        assertThat(game.standings().points(p(0)), is(0));
        assertThat(game.standings().ranked(), contains(p(1), p(2), p(3)));
    }

    @Test
    void aForfeitThatLeavesOneHolderEndsTheRoundAndTheMatchGoesOnWithTwoPlayers() throws Exception {
        President game = aPresident().withHand("3C").withHand("4C 9C").withHand("5C JC").leader(0).build();
        game.play(p(0), cards("3C"));

        game.forfeit(p(1));

        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.isFinished(), is(false));
        assertThat(game.lastRoundResult().finishingOrder(), contains(p(0), p(2)));
        assertThat(game.lastRoundResult().titles(), is(Map.of(p(0), Title.PRESIDENT, p(2), Title.SCUM)));
        assertThat(game.lastRoundResult().points(), is(Map.of(p(0), 1, p(2), 0)));
        assertThat(game.getPlayerIds(), contains(p(0), p(2)));
        assertThat(game.standings().ranked(), contains(p(0), p(2)));
        // the forfeiter's hand is set aside; the last trick stays on the table
        assertThat(game.discard(), containsInAnyOrder(card("4C"), card("9C")));
        assertThat(game.trick().plays().size(), is(1));
    }

    @Test
    void theMatchContinuesWithTwoPlayersAndExchangesBetweenThem() throws Exception {
        President game = aPresident().withHand("3C").withHand("4C 9C").withHand("5C JC").leader(0).build();
        game.play(p(0), cards("3C"));
        game.forfeit(p(1));

        game.ready(p(0));
        game.ready(p(2));

        assertThat(game.round(), is(2));
        assertThat(game.titles(), is(Map.of(p(0), Title.PRESIDENT, p(2), Title.SCUM)));
        assertThat(game.dealer(), is(p(2)));
        assertThat(game.phase(), is(Phase.EXCHANGE));
        assertThat(game.exchange(), contains(new PendingReturn(p(0), p(2), 2, false)));
        assertThat(game.hand(p(1)), is(empty()));
        assertThat(PresidentDriver.wholeDeck(game, 52), is(true));
    }

    @Test
    void oneLeftPlayerWinsByForfeit() throws Exception {
        President game = threePlayers();

        game.forfeit(p(1));
        assertThat(game.isFinished(), is(false));
        game.forfeit(p(2));

        assertThat(game.isFinished(), is(true));
        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new PresidentResult(List.of(p(0)), PresidentResult.Reason.FORFEIT)));
        assertThrows(FinishedGameException.class, () -> game.play(p(0), cards("3C")));
    }

    @Test
    void forfeitingTwiceOrAnUnknownSeatOrAfterTheEndChangesNothing() throws Exception {
        President game = threePlayers();
        game.forfeit(p(1));
        int version = game.version();

        game.forfeit(p(1));
        game.forfeit(p(9));
        game.forfeit(null);

        assertThat(game.version(), is(version));
        game.forfeit(p(2));
        int finalVersion = game.version();
        game.forfeit(p(0));
        assertThat(game.version(), is(finalVersion));
        assertThat(game.result().winners(), is(List.of(p(0))));
    }

    // ----- ROUND_OVER -----

    @Test
    void aForfeitAtTheBarrierDropsTheirPointsAndOpensItWhenTheOthersAreReady() throws Exception {
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults().withExchange(ExchangeAmount.NONE), 1, 3, 0, 2);
        game.ready(p(0));
        game.ready(p(1));
        game.ready(p(2));
        assertThat(game.round(), is(1));

        game.forfeit(p(3));

        assertThat(game.round(), is(2));
        assertThat(game.getPlayerIds(), contains(p(0), p(1), p(2)));
        assertThat(game.standings().ranked().contains(p(3)), is(false));
        // the previous order without the leaver: President 1, Neutral 0, Scum 2, who deals
        assertThat(game.titles(), is(Map.of(p(1), Title.PRESIDENT, p(0), Title.NEUTRAL, p(2), Title.SCUM)));
        assertThat(game.dealer(), is(p(2)));
        assertThat(game.hand(p(3)), is(empty()));
        assertThat(game.handSizes(), is(List.of(18, 17, 17, 0)));
        assertThat(PresidentDriver.wholeDeck(game, 52), is(true));
        assertThat(game.currentSeat(), is(p(2)));
    }

    @Test
    void aForfeitAtTheBarrierFromANotReadyPlayerKeepsTheBarrierClosedUntilTheRestIsReady() throws Exception {
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), 1, 3, 0, 2);
        game.ready(p(0));

        game.forfeit(p(2));

        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.readySeats(), is(Set.of(p(0))));
        game.ready(p(1));
        game.ready(p(3));
        assertThat(game.round(), is(2));
    }

    @Test
    void aReadyPlayerWhoForfeitsLeavesTheReadySet() throws Exception {
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), 1, 3, 0, 2);
        game.ready(p(0));
        game.ready(p(1));

        game.forfeit(p(0));

        assertThat(game.readySeats(), is(Set.of(p(1))));
    }

    @Test
    void oneLeftAtTheBarrierFinishesTheMatchByForfeit() throws Exception {
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), 0, 1, 2);

        game.forfeit(p(0));
        game.forfeit(p(1));

        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new PresidentResult(List.of(p(2)), PresidentResult.Reason.FORFEIT)));
    }

    // ----- EXCHANGE: round 2 of the 5-player scenario of ExchangeFlowTest -----

    private ScriptedDeckSource source;
    private GameId id;

    private President exchange() throws Exception {
        source = new ScriptedDeckSource(new RandomDeckSource());
        id = GameId.generate();
        source.fix(id, 2, PresidentDeals.deck(5, 3,
                Map.of(3, "2S 2H", 1, "2D", 4, "2C", 2, "3C 3D 4C", 0, "3H 4D")));
        President game = OrderedRounds.playedRoundOne(id, source, PresidentOptions.defaults(), 2, 0, 4, 1, 3);
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }
        assertThat(game.phase(), is(Phase.EXCHANGE));
        return game;
    }

    @Test
    void aPresidentWhoForfeitsDuringTheExchangeGivesBackTheirLowestCardsFirst() throws Exception {
        President game = exchange();
        List<Card> lowest = game.hand(p(2)).subList(0, 2);

        game.forfeit(p(2));

        assertThat(game.received(p(3)), is(lowest));
        assertThat(game.hand(p(3)).containsAll(lowest), is(true));
        assertThat(game.hand(p(2)), is(empty()));
        assertThat(game.discard(), hasItems(card("2S"), card("2H")));
        assertThat(game.phase(), is(Phase.EXCHANGE));
        assertThat(game.pendingReturns(), contains(new PendingReturn(p(0), p(1), 1, false)));
        assertThat(PresidentDriver.wholeDeck(game, 52), is(true));
    }

    @Test
    void thePendingReturnCompletedByTheForfeitCanBeTheLastOneAndPlayStarts() throws Exception {
        President game = exchange();
        game.giveBack(p(0), game.hand(p(0)).subList(0, 1));

        game.forfeit(p(2));

        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.pendingReturns(), is(empty()));
        assertThat(PresidentDriver.wholeDeck(game, 52), is(true));
    }

    @Test
    void aVicePresidentWhoForfeitsGivesBackTheirLowestCardAndTheOtherPairStillReturns() throws Exception {
        President game = exchange();
        List<Card> lowest = game.hand(p(0)).subList(0, 1);

        game.forfeit(p(0));

        assertThat(game.received(p(1)), is(lowest));
        assertThat(game.pendingReturns(), contains(new PendingReturn(p(2), p(3), 2, false)));
        game.giveBack(p(2), game.hand(p(2)).subList(0, 2));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(3)));
    }

    @Test
    void aLowRankWhoForfeitsCancelsTheirPairAndTheLeadMovesToTheNextSeatWithCards() throws Exception {
        President game = exchange();
        int presidentCards = game.hand(p(2)).size();

        game.forfeit(p(3));

        assertThat(game.hand(p(2)).size(), is(presidentCards));
        assertThat(game.pendingReturns(), contains(new PendingReturn(p(0), p(1), 1, false)));
        game.giveBack(p(0), game.hand(p(0)).subList(0, 1));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(4)));
        assertThat(game.trick().leader(), is(p(4)));
        assertThat(PresidentDriver.wholeDeck(game, 52), is(true));
    }

    @Test
    void anUninvolvedNeutralMayForfeitDuringTheExchange() throws Exception {
        President game = exchange();

        game.forfeit(p(4));

        assertThat(game.phase(), is(Phase.EXCHANGE));
        assertThat(game.pendingReturns().size(), is(2));
        assertThat(game.getPlayerIds(), contains(p(0), p(1), p(2), p(3)));
    }

    @Test
    void forfeitsDownToOnePlayerDuringTheExchangeFinishTheMatch() throws Exception {
        President game = exchange();

        game.forfeit(p(4));
        game.forfeit(p(0));
        game.forfeit(p(1));
        game.forfeit(p(3));

        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new PresidentResult(List.of(p(2)), PresidentResult.Reason.FORFEIT)));
    }
}
