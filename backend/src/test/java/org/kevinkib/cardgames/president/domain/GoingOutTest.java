package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.RoundOver;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.round.Phase;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class GoingOutTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    @Test
    void aPlayerWhoPlaysTheirLastCardTakesTheNextFreePlaceAndTheRoundGoesOn() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").withHand("JC JD")
                .leader(0).build();

        List<PresidentEvent> events = game.play(p(0), cards("4C"));

        assertThat(events, hasItem(new PlayerOut(p(0), 1)));
        assertThat(game.finishingOrder(), contains(p(0)));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.hand(p(0)), is(empty()));
        assertThat(game.currentSeat(), is(p(1)));
    }

    @Test
    void playContinuesAmongTheOthersAndThePlayerWhoWentOutIsNeverAskedAgain() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").withHand("JC JD")
                .leader(0).build();

        game.play(p(0), cards("4C"));
        game.pass(p(1));
        game.pass(p(2));
        game.pass(p(3));

        assertThat(game.currentSeat(), is(p(1)));
        game.play(p(1), cards("5C"));
        game.pass(p(2));
        game.pass(p(3));
        assertThat(game.currentSeat(), is(p(1)));
    }

    @Test
    void theRoundEndsWhenOnlyOnePlayerStillHoldsCardsAndThatPlayerTakesTheLastPlace() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0).build();

        game.play(p(0), cards("4C"));
        game.play(p(1), cards("5C"));
        game.pass(p(2));
        List<PresidentEvent> events = game.play(p(1), cards("6C"));

        assertThat(events, hasItem(new PlayerOut(p(1), 2)));
        assertThat(events.get(events.size() - 1), is(new RoundOver(List.of(p(0), p(1), p(2)))));
        assertThat(game.finishingOrder(), contains(p(0), p(1), p(2)));
        assertThat(game.phase(), is(Phase.ROUND_OVER));
        // a game is a match of several rounds: one round over does not finish it
        assertThat(game.isFinished(), is(false));
        // the last player is not obliged to play their cards out
        assertThat(game.hand(p(2)).size(), is(3));
        assertThat(game.roundTitles(), is(Map.of(p(0), Title.PRESIDENT, p(1), Title.NEUTRAL, p(2), Title.SCUM)));
    }

    /** Every seat holds one card, stronger than the previous seat's: they go out in seat order. */
    @ParameterizedTest(name = "{0} players: {1}")
    @CsvSource(delimiter = '|', value = {
            "3 | PRESIDENT NEUTRAL SCUM",
            "4 | PRESIDENT VICE_PRESIDENT VICE_SCUM SCUM",
            "5 | PRESIDENT VICE_PRESIDENT NEUTRAL VICE_SCUM SCUM",
            "8 | PRESIDENT VICE_PRESIDENT NEUTRAL NEUTRAL NEUTRAL NEUTRAL VICE_SCUM SCUM",
    })
    void titlesFollowTheFinishingOrder(int players, String expected) throws Exception {
        String[] ranks = {"3", "4", "5", "6", "7", "8", "9", "10"};
        PresidentBuilder builder = aPresident().leader(0);
        for (int seat = 0; seat < players; seat++) {
            builder.withHand(ranks[seat] + "C");
        }
        President game = builder.build();

        for (int seat = 0; seat < players - 1; seat++) {
            game.play(p(seat), cards(ranks[seat] + "C"));
        }

        assertThat(game.phase(), is(Phase.ROUND_OVER));
        List<PlayerId> order = game.finishingOrder();
        assertThat(order.size(), is(players));
        String[] titles = expected.split(" ");
        Map<PlayerId, Title> roundTitles = game.roundTitles();
        for (int place = 0; place < players; place++) {
            assertThat(order.get(place), is(p(place)));
            assertThat(roundTitles.get(order.get(place)), is(Title.valueOf(titles[place])));
        }
        assertThat(Arrays.stream(titles).count(), is((long) players));
    }

    @Test
    void titlesAreNotAvailableBeforeTheRoundIsOver() {
        President game = aPresident().withHand("4C").withHand("5C").withHand("6C").leader(0).build();

        assertThat(game.roundTitles(), is(Map.of()));
    }
}
