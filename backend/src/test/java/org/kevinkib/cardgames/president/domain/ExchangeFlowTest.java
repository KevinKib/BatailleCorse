package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.CardsGivenBack;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.RoundDealt;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.exchange.WrongGiveBackException;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.CardsNotInHandException;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.round.WrongPhaseException;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.GiveBack;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.seeExchangeAll;

/**
 * Round 2 of a 5-player match whose first round ended 2, 0, 4, 1, 3: seat 2 is President, seat 0 Vice-President,
 * seat 4 Neutral, seat 1 Vice-Scum and seat 3 Scum (and so the dealer). The deal is fixed so that the Scum
 * holds the two best cards (both 2s of spades and hearts) and the Vice-Scum the 2 of diamonds.
 */
class ExchangeFlowTest {

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

    private President roundTwo() throws Exception {
        return roundTwo(PresidentOptions.defaults());
    }

    private static List<Card> lowest(President game, int seat, int count) {
        return game.hand(p(seat)).subList(0, count);
    }

    @Test
    void roundOneHasNoTitlesAndNoExchange() throws Exception {
        President game = OrderedRounds.start(id, source, PresidentOptions.defaults(), ORDER);

        assertThat(game.round(), is(1));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.titles().isEmpty(), is(true));
        assertThat(game.exchange(), is(empty()));
    }

    @Test
    void theBestCardsOfTheLowRanksMoveAtTheDealAndTheHighRanksMustReturn() throws Exception {
        President game = roundTwo();

        assertThat(game.round(), is(2));
        assertThat(game.phase(), is(Phase.EXCHANGE));
        assertThat(game.dealer(), is(p(3)));
        assertThat(game.titles(), is(Map.of(p(2), Title.PRESIDENT, p(0), Title.VICE_PRESIDENT,
                p(4), Title.NEUTRAL, p(1), Title.VICE_SCUM, p(3), Title.SCUM)));
        assertThat(game.hand(p(3)), not(hasItems(card("2S"), card("2H"))));
        assertThat(game.hand(p(2)), hasItems(card("2S"), card("2H")));
        assertThat(game.hand(p(1)), not(hasItem(card("2D"))));
        assertThat(game.hand(p(0)), hasItem(card("2D")));
        assertThat(game.hand(p(4)), hasItem(card("2C")));
        assertThat(game.handSizes(), is(List.of(12, 9, 12, 8, 11)));
        assertThat(game.exchange(), contains(new PendingReturn(p(2), p(3), 2, false),
                new PendingReturn(p(0), p(1), 1, false)));
        assertThat(game.pendingReturns(), is(game.exchange()));
    }

    @Test
    void theDealerOfRoundTwoIsThePreviousScumAndTheExtraCardsGoRightAfter() throws Exception {
        President game = roundTwo(PresidentOptions.defaults().withExchange(ExchangeAmount.NONE));

        assertThat(game.dealer(), is(p(3)));
        assertThat(game.handSizes(), is(List.of(11, 10, 10, 10, 11)));
    }

    @Test
    void returnsComeInAnyOrderAndPlayStartsAfterTheLastOne() throws Exception {
        President game = roundTwo();
        int version = game.version();

        List<PresidentEvent> first = game.giveBack(p(0), lowest(game, 0, 1));

        assertThat(first, hasItem(new CardsGivenBack(p(0), p(1), 1)));
        assertThat(game.version(), is(version + 1));
        assertThat(game.phase(), is(Phase.EXCHANGE));
        assertThat(game.pendingReturns(), contains(new PendingReturn(p(2), p(3), 2, false)));

        List<PresidentEvent> last = game.giveBack(p(2), lowest(game, 2, 2));

        assertThat(last, hasItem(new CardsGivenBack(p(2), p(3), 2)));
        assertThat(last.stream().anyMatch(e -> e instanceof RoundDealt), is(false));
        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.pendingReturns(), is(empty()));
        seeExchangeAll(game);
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.handSizes(), is(List.of(11, 10, 10, 10, 11)));
        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.trick().plays(), is(empty()));
    }

    @Test
    void thePresidentMayLeadFirstWhenTheOptionSaysSo() throws Exception {
        President game = roundTwo(PresidentOptions.defaults().withNextLead(NextLead.PRESIDENT));

        game.giveBack(p(2), lowest(game, 2, 2));
        game.giveBack(p(0), lowest(game, 0, 1));

        assertThat(game.currentSeat(), is(p(2)));
    }

    @Test
    void aHighRankMayGiveBackTheCardsJustReceived() throws Exception {
        President game = roundTwo();

        game.giveBack(p(2), cards("2S 2H"));

        assertThat(game.hand(p(3)), hasItems(card("2S"), card("2H")));
        assertThat(game.given(p(2)), is(cards("2S 2H")));
    }

    @Test
    void aReturnOfTheWrongSizeIsRefusedWithoutEffect() throws Exception {
        President game = roundTwo();
        int version = game.version();
        List<Integer> sizes = game.handSizes();

        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(2), lowest(game, 2, 1)));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(2), lowest(game, 2, 3)));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(2), List.of()));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(2), null));

        assertThat(game.version(), is(version));
        assertThat(game.handSizes(), is(sizes));
        assertThat(game.phase(), is(Phase.EXCHANGE));
    }

    @Test
    void aSeatWithNothingToReturnIsRefused() throws Exception {
        President game = roundTwo();
        int version = game.version();

        // the Scum, the Vice-Scum and the Neutral receive or give nothing back; an unknown seat neither
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(3), lowest(game, 3, 2)));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(1), lowest(game, 1, 1)));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(4), lowest(game, 4, 1)));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(9), cards("3C")));
        assertThrows(WrongGiveBackException.class, () -> game.giveBack(null, cards("3C")));

        assertThat(game.version(), is(version));
    }

    @Test
    void aReturnCanOnlyBeMadeOnce() throws Exception {
        President game = roundTwo();
        game.giveBack(p(0), lowest(game, 0, 1));
        int version = game.version();

        assertThrows(WrongGiveBackException.class, () -> game.giveBack(p(0), lowest(game, 0, 1)));

        assertThat(game.version(), is(version));
    }

    @Test
    void cardsThatAreNotInTheHandAreRefusedWithoutEffect() throws Exception {
        President game = roundTwo();
        int version = game.version();

        assertThrows(CardsNotInHandException.class, () -> game.giveBack(p(2), cards("2C 2D")));
        assertThrows(CardsNotInHandException.class, () -> game.giveBack(p(2), cards("3C 3C")));

        assertThat(game.version(), is(version));
        assertThat(game.pendingReturns().size(), is(2));
    }

    @Test
    void thePlayingCommandsAreRefusedDuringTheExchangeAndTheExchangeDuringPlay() throws Exception {
        President game = roundTwo();
        int version = game.version();

        assertThrows(WrongPhaseException.class, () -> game.play(p(3), cards("2C")));
        assertThrows(WrongPhaseException.class, () -> game.pass(p(3)));
        assertThrows(WrongPhaseException.class, () -> game.ready(p(3)));
        assertThat(game.version(), is(version));

        game.giveBack(p(2), lowest(game, 2, 2));
        game.giveBack(p(0), lowest(game, 0, 1));

        assertThrows(WrongPhaseException.class, () -> game.giveBack(p(2), lowest(game, 2, 2)));
    }

    @Test
    void eachSeatSeesOnlyItsOwnExchangeCards() throws Exception {
        President game = roundTwo();
        List<Card> returned = lowest(game, 2, 2);

        assertThat(game.received(p(2)), containsInAnyOrder(card("2S"), card("2H")));
        assertThat(game.given(p(3)), containsInAnyOrder(card("2S"), card("2H")));
        assertThat(game.received(p(0)), is(cards("2D")));
        assertThat(game.given(p(1)), is(cards("2D")));
        for (int uninvolved : new int[]{4}) {
            assertThat(game.received(p(uninvolved)), is(empty()));
            assertThat(game.given(p(uninvolved)), is(empty()));
        }
        // before the return the low ranks have received nothing and the high ranks given nothing
        assertThat(game.received(p(3)), is(empty()));
        assertThat(game.given(p(2)), is(empty()));

        game.giveBack(p(2), returned);

        assertThat(game.given(p(2)), is(returned));
        assertThat(game.received(p(3)), is(returned));
        assertThat(game.given(p(0)), is(empty()));
    }

    @Test
    void thePendingReturnsOfTheOthersShowOnlyCounts() throws Exception {
        President game = roundTwo();

        // the public view of the exchange carries seats and counts, never a card
        for (PendingReturn pending : game.exchange()) {
            assertThat(pending.count() > 0, is(true));
        }
        assertThat(PendingReturn.class.getRecordComponents().length, is(4));
    }

    @Test
    void lowestGiveBackIsAutomaticAndSkipsTheExchangePhase() throws Exception {
        President game = roundTwo(PresidentOptions.defaults().withGiveBack(GiveBack.LOWEST));

        assertThat(game.phase(), is(Phase.EXCHANGE_VIEW));
        assertThat(game.pendingReturns(), is(empty()));
        assertThat(game.handSizes(), is(List.of(11, 10, 10, 10, 11)));
        assertThat(game.currentSeat(), is(p(3)));
        RankOrder order = RankOrder.standard();
        List<Card> given = game.given(p(2));
        assertThat(given.size(), is(2));
        assertThat(game.received(p(3)), is(given));
        for (Card card : given) {
            for (Card kept : game.hand(p(2))) {
                assertThat(order.compare(card.getRank(), kept.getRank()) <= 0, is(true));
            }
        }
        assertThat(game.given(p(0)).size(), is(1));
        assertThat(game.received(p(1)), is(game.given(p(0))));
        // the high ranks keep what they received: the 2s are not among the lowest cards
        assertThat(game.hand(p(2)), hasItems(card("2S"), card("2H")));
    }

    @Test
    void noExchangeMeansNoPhaseAndNoMovedCard() throws Exception {
        President game = roundTwo(PresidentOptions.defaults().withExchange(ExchangeAmount.NONE));

        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.exchange(), is(empty()));
        assertThat(game.hand(p(3)), hasItems(card("2S"), card("2H")));
        assertThat(game.given(p(3)), is(empty()));
        assertThat(game.titles().get(p(2)), is(Title.PRESIDENT));
    }

    @Test
    void withThreePlayersOnlyThePresidentAndTheScumExchange() throws Exception {
        source.fix(id, 2, PresidentDeals.deck(3, 2, Map.of(2, "2S 2H", 0, "3C 4C")));
        President game = OrderedRounds.playedRoundOne(id, source, PresidentOptions.defaults(), 0, 1, 2);

        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }

        assertThat(game.phase(), is(Phase.EXCHANGE));
        assertThat(game.exchange(), contains(new PendingReturn(p(0), p(2), 2, false)));
        assertThat(game.titles().get(p(1)), is(Title.NEUTRAL));
    }

    @Test
    void thePlayerWhoLeadsAfterTheExchangeCanPlay() throws Exception {
        President game = roundTwo();
        game.giveBack(p(2), lowest(game, 2, 2));
        game.giveBack(p(0), lowest(game, 0, 1));
        seeExchangeAll(game);

        List<Play> plays = game.legalPlays(p(3));

        assertThat(plays.isEmpty(), is(false));
        game.play(p(3), plays.get(0).cards());
        assertThat(game.trick().plays().size(), is(1));
    }
}
