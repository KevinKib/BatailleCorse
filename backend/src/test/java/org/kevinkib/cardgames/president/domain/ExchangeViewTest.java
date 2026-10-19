package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.CardsGivenBack;
import org.kevinkib.cardgames.president.domain.event.ExchangeSeen;
import org.kevinkib.cardgames.president.domain.event.ExchangeViewClosed;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.PresidentResult;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.round.WrongPhaseException;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.GiveBack;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The exchange view barrier. Round 2 of the 5-player match of {@code ExchangeFlowTest} (first round ended
 * 2, 0, 4, 1, 3): seat 2 is President, 0 Vice-President, 4 Neutral, 1 Vice-Scum and 3 Scum, who deals and leads.
 * Seat 2 returns two cards to seat 3, seat 0 one card to seat 1.
 */
class ExchangeViewTest {

    private static final int[] ORDER = {2, 0, 4, 1, 3};

    private ScriptedDeckSource source;
    private GameId id;

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    @BeforeEach
    void setUp() {
        source = new ScriptedDeckSource(new RandomDeckSource());
        id = GameId.generate();
        source.fix(id, 2, PresidentDeals.deck(5, 3,
                Map.of(3, "2S 2H", 1, "2D", 4, "2C", 2, "3C 3D 4C", 0, "3H 4D")));
    }

    private President roundTwo(PresidentOptions options) throws Exception {
        President game = OrderedRounds.playedRoundOne(id, source, options, ORDER);
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }
        return game;
    }

    private static void giveBackAll(President game) throws Exception {
        game.giveBack(p(0), game.hand(p(0)).subList(0, 1));
        game.giveBack(p(2), game.hand(p(2)).subList(0, 2));
    }

    /** Round 2 with both returns made: the view is open and nobody has seen it. */
    private President inTheView(PresidentOptions options) throws Exception {
        President game = roundTwo(options);
        giveBackAll(game);
        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        return game;
    }

    private President inTheView() throws Exception {
        return inTheView(PresidentOptions.defaults());
    }

    // ----- entering the view -----

    @Test
    void theLastFreeReturnOpensTheViewInsteadOfThePlay() throws Exception {
        President game = roundTwo(PresidentOptions.defaults());
        game.giveBack(p(0), game.hand(p(0)).subList(0, 1));
        assertThat(game.phase(), is(Phase.EXCHANGE));
        int version = game.version();

        List<PresidentEvent> events = game.giveBack(p(2), game.hand(p(2)).subList(0, 2));

        assertThat(events, hasItem(new CardsGivenBack(p(2), p(3), 2)));
        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.version(), is(version + 1));
        assertThat(game.pendingReturns(), is(empty()));
        assertThat(game.exchangeSeen(), is(empty()));
        assertThat(game.trick().plays(), is(empty()));
    }

    @Test
    void theLowestGiveBackOpensTheViewAtTheDeal() throws Exception {
        President game = roundTwo(PresidentOptions.defaults().withGiveBack(GiveBack.LOWEST));

        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.pendingReturns(), is(empty()));
        assertThat(game.exchangeSeen(), is(empty()));
        assertThat(game.availableActions(p(4)), is(List.of(Action.SEE_EXCHANGE)));
    }

    @Test
    void roundOneAndARoundWithoutExchangeGoStraightToThePlay() throws Exception {
        President first = OrderedRounds.start(id, source, PresidentOptions.defaults(), ORDER);
        President none = roundTwo(PresidentOptions.defaults().withExchange(ExchangeAmount.NONE));

        assertThat(first.phase(), is(Phase.PLAYING));
        assertThat(none.phase(), is(Phase.PLAYING));
        assertThat(none.round(), is(2));
        assertThat(none.availableActions(p(3)).contains(Action.SEE_EXCHANGE), is(false));
    }

    // ----- acknowledgements -----

    @Test
    void anAcknowledgementIsRecordedAndOnlyTheLastOneBeginsThePlay() throws Exception {
        President game = inTheView();
        int version = game.version();

        List<PresidentEvent> events = game.seeExchange(p(4), 2);

        assertThat(events, is(List.of(new ExchangeSeen(p(4), false))));
        assertThat(game.version(), is(version + 1));
        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.exchangeSeen(), is(Set.of(p(4))));
    }

    @Test
    void theLastAcknowledgementBeginsThePlayAndTheScumLeads() throws Exception {
        President game = inTheView();
        for (int seat : new int[]{0, 1, 2, 3}) {
            assertThat(game.seeExchange(p(seat), 2), is(List.of(new ExchangeSeen(p(seat), false))));
        }
        int version = game.version();

        List<PresidentEvent> events = game.seeExchange(p(4), 2);

        assertThat(events, is(List.of(new ExchangeSeen(p(4), true))));
        assertThat(game.version(), is(version + 1));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.exchangeSeen(), is(empty()));
        assertThat(game.legalPlays(p(3)).isEmpty(), is(false));
    }

    @Test
    void thePresidentLeadsWhenTheOptionSaysSo() throws Exception {
        President game = inTheView(PresidentOptions.defaults().withNextLead(NextLead.PRESIDENT));
        assertThat(game.currentSeat(), is(p(2)));

        PresidentFixtures.seeExchangeAll(game);

        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(2)));
    }

    @Test
    void aRepeatedAcknowledgementChangesNothing() throws Exception {
        President game = inTheView();
        game.seeExchange(p(4), 2);
        int version = game.version();

        assertThat(game.seeExchange(p(4), 2), is(empty()));

        assertThat(game.version(), is(version));
        assertThat(game.exchangeSeen(), is(Set.of(p(4))));
    }

    @Test
    void anAcknowledgementOfAnotherRoundChangesNothing() throws Exception {
        President game = inTheView();
        int version = game.version();

        assertThat(game.seeExchange(p(4), 1), is(empty()));
        assertThat(game.seeExchange(p(4), 3), is(empty()));

        assertThat(game.version(), is(version));
        assertThat(game.exchangeSeen(), is(empty()));
    }

    @Test
    void anAcknowledgementOutsideTheViewChangesNothing() throws Exception {
        President exchange = roundTwo(PresidentOptions.defaults());
        President playing = inTheView();
        PresidentFixtures.seeExchangeAll(playing);
        int exchangeVersion = exchange.version();
        int playingVersion = playing.version();

        assertThat(exchange.seeExchange(p(4), 2), is(empty()));
        assertThat(playing.seeExchange(p(4), 2), is(empty()));

        assertThat(exchange.version(), is(exchangeVersion));
        assertThat(playing.version(), is(playingVersion));
        assertThat(exchange.phase(), is(Phase.EXCHANGE));
        assertThat(playing.phase(), is(Phase.PLAYING));
    }

    @Test
    void aSeatThatLeftIsRefusedAsNotPlaying() throws Exception {
        President game = inTheView();
        game.forfeit(p(4));
        int version = game.version();

        assertThrows(NotPlayersTurnException.class, () -> game.seeExchange(p(4), 2));
        assertThrows(NotPlayersTurnException.class, () -> game.seeExchange(p(9), 2));
        assertThrows(NotPlayersTurnException.class, () -> game.seeExchange(null, 2));

        assertThat(game.version(), is(version));
    }

    @Test
    void afterTheMatchEndsAnAcknowledgementChangesNothing() throws Exception {
        President game = inTheView();
        game.forfeit(p(4));
        game.forfeit(p(0));
        game.forfeit(p(1));
        game.forfeit(p(3));
        assertThat(game.isFinished(), is(true));
        int version = game.version();

        assertThat(game.seeExchange(p(2), 2), is(empty()));
        assertThat(game.closeExchangeView(2), is(empty()));

        assertThat(game.version(), is(version));
    }

    // ----- actions and refused commands -----

    @Test
    void theOnlyActionDuringTheViewIsToSeeItUntilTheSeatHas() throws Exception {
        President game = inTheView();

        for (int seat = 0; seat < 5; seat++) {
            assertThat(game.availableActions(p(seat)), is(List.of(Action.SEE_EXCHANGE)));
        }
        game.seeExchange(p(3), 2);

        assertThat(game.availableActions(p(3)), is(empty()));
        assertThat(game.availableActions(p(2)), is(List.of(Action.SEE_EXCHANGE)));
    }

    @Test
    void aSeatThatLeftHasNoActionInTheView() throws Exception {
        President game = inTheView();
        game.forfeit(p(4));

        assertThat(game.availableActions(p(4)), is(empty()));
    }

    @Test
    void playPassAndGiveBackAreRefusedDuringTheView() throws Exception {
        President game = inTheView();
        int version = game.version();
        PlayerId leader = game.currentSeat();

        assertThrows(WrongPhaseException.class, () -> game.play(leader, cards("3C")));
        assertThrows(WrongPhaseException.class, () -> game.pass(leader));
        assertThrows(WrongPhaseException.class, () -> game.giveBack(p(2), game.hand(p(2)).subList(0, 2)));
        assertThrows(WrongPhaseException.class, () -> game.ready(p(0)));

        assertThat(game.version(), is(version));
        assertThat(game.legalPlays(leader), is(empty()));
    }

    @Test
    void theLeaderIsTheFutureFirstLeadDuringTheView() throws Exception {
        President game = inTheView();

        assertThat(game.currentSeat(), is(p(3)));
    }

    // ----- the close -----

    @Test
    void theCloseBeginsPlayAndNamesTheLateSeatsInSeatOrder() throws Exception {
        President game = inTheView();
        game.seeExchange(p(2), 2);
        game.seeExchange(p(0), 2);
        int version = game.version();

        List<PresidentEvent> events = game.closeExchangeView(2);

        assertThat(events, is(List.of(new ExchangeViewClosed(2, List.of(p(1), p(3), p(4))))));
        assertThat(game.version(), is(version + 1));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.exchangeSeen(), is(empty()));
    }

    @Test
    void theCloseWhenNobodySawItNamesEveryone() throws Exception {
        President game = inTheView();

        List<PresidentEvent> events = game.closeExchangeView(2);

        assertThat(events, is(List.of(new ExchangeViewClosed(2, List.of(p(0), p(1), p(2), p(3), p(4))))));
        assertThat(game.phase(), is(Phase.PLAYING));
    }

    @Test
    void aStaleCloseDoesNothing() throws Exception {
        President view = inTheView();
        President exchange = roundTwo(PresidentOptions.defaults());
        President playing = inTheView();
        PresidentFixtures.seeExchangeAll(playing);
        int viewVersion = view.version();
        int exchangeVersion = exchange.version();
        int playingVersion = playing.version();

        assertThat(view.closeExchangeView(1), is(empty()));
        assertThat(view.closeExchangeView(3), is(empty()));
        assertThat(exchange.closeExchangeView(2), is(empty()));
        assertThat(playing.closeExchangeView(2), is(empty()));

        assertThat(view.version(), is(viewVersion));
        assertThat(view.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(exchange.version(), is(exchangeVersion));
        assertThat(playing.version(), is(playingVersion));
    }

    @Test
    void aSecondCloseDoesNothing() throws Exception {
        President game = inTheView();
        game.closeExchangeView(2);
        int version = game.version();

        assertThat(game.closeExchangeView(2), is(empty()));

        assertThat(game.version(), is(version));
    }

    // ----- forfeits -----

    @Test
    void aForfeitOfTheLastSeatToSeeItBeginsPlayInTheSameCommand() throws Exception {
        President game = inTheView();
        for (int seat : new int[]{0, 1, 2, 3}) {
            game.seeExchange(p(seat), 2);
        }
        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));

        game.forfeit(p(4));

        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(3)));
    }

    @Test
    void aForfeitOfASeatThatSawItLeavesTheOthersWaiting() throws Exception {
        President game = inTheView();
        game.seeExchange(p(4), 2);
        game.seeExchange(p(2), 2);

        game.forfeit(p(4));

        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.exchangeSeen(), is(Set.of(p(2))));
        assertThat(game.getPlayerIds(), contains(p(0), p(1), p(2), p(3)));
    }

    @Test
    void aForfeitOfASeatStillWaitingKeepsTheOthersWaiting() throws Exception {
        President game = inTheView();
        game.seeExchange(p(2), 2);

        game.forfeit(p(4));

        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.availableActions(p(0)), is(List.of(Action.SEE_EXCHANGE)));
        game.seeExchange(p(0), 2);
        game.seeExchange(p(1), 2);
        game.seeExchange(p(3), 2);
        assertThat(game.phase(), is(Phase.PLAYING));
    }

    @Test
    void whenTheFirstLeadLeavesDuringTheViewTheLeadMovesToTheNextSeatWithCards() throws Exception {
        President game = inTheView();
        assertThat(game.currentSeat(), is(p(3)));

        game.forfeit(p(3));

        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.currentSeat(), is(p(4)));
        assertThat(game.trick().leader(), is(p(4)));
        PresidentFixtures.seeExchangeAll(game);
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(4)));
        assertThat(PresidentDriver.wholeDeck(game, 52), is(true));
    }

    @Test
    void aForfeitDownToOnePlayerDuringTheViewFinishesTheMatch() throws Exception {
        President game = inTheView();

        game.forfeit(p(4));
        game.forfeit(p(0));
        game.forfeit(p(1));
        game.forfeit(p(3));

        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new PresidentResult(List.of(p(2)), PresidentResult.Reason.FORFEIT)));
        assertThat(game.availableActions(p(2)), is(empty()));
    }

    @Test
    void aForfeitThatSettlesTheLastReturnOpensTheView() throws Exception {
        President game = roundTwo(PresidentOptions.defaults());
        game.giveBack(p(0), game.hand(p(0)).subList(0, 1));

        game.forfeit(p(2));

        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.pendingReturns(), is(empty()));
        assertThat(game.exchangeSeen(), is(empty()));
        assertThat(game.availableActions(p(0)), is(List.of(Action.SEE_EXCHANGE)));
        assertThat(game.availableActions(p(2)), is(empty()));
        PresidentFixtures.seeExchangeAll(game);
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.currentSeat(), is(p(3)));
    }

    @Test
    void aRoundWhoseReturnsWereAllCancelledByForfeitsStillOpensTheView() throws Exception {
        President game = roundTwo(PresidentOptions.defaults());

        game.forfeit(p(3));
        assertThat(game.phase(), is(Phase.EXCHANGE));
        game.forfeit(p(1));

        assertThat(game.pendingReturns(), is(empty()));
        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.currentSeat(), is(p(4)));
    }

    // ----- the next round -----

    @Test
    void theNextDealClearsTheAcknowledgements() throws Exception {
        President game = inTheView();
        game.seeExchange(p(4), 2);
        game.closeExchangeView(2);
        java.util.Random random = new java.util.Random(7);
        while (game.round() == 2) {
            PresidentDriver.step(game, random);
        }

        assertThat(game.round(), is(3));
        assertThat(game.exchangeSeen(), is(empty()));
    }
}
