package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class PresidentBotViewTest {

    private static President table() throws Exception {
        President game = aPresident().withHand("5H 9D KC").withHand("6H 6D AS").withHand("3C 7S 10H").leader(0).build();
        game.play(new PlayerId(0), cards("5H"));
        return game;
    }

    /** Every card the view can name: the seat's hand, the trick, its legal plays and its exchange cards. */
    private static List<Card> cardsShown(PresidentBotView view) {
        List<Card> shown = new ArrayList<>(view.hand());
        view.trick().plays().forEach(p -> shown.addAll(p.play().cards()));
        view.legalPlays().forEach(p -> shown.addAll(p.cards()));
        shown.addAll(view.received());
        shown.addAll(view.given());
        return shown;
    }

    @Test
    void theViewShowsTheSeatsOwnHandAndNoOtherHand() throws Exception {
        President game = table();

        PresidentBotView view = PresidentBotView.forSeat(game, new PlayerId(1));

        assertThat(view.hand(), is(game.hand(new PlayerId(1))));
        List<Card> shown = cardsShown(view);
        assertThat(shown, not(hasItem(cards("3C").get(0))));
        assertThat(shown, not(hasItem(cards("7S").get(0))));
        assertThat(shown, not(hasItem(cards("10H").get(0))));
        assertThat(shown, not(hasItem(cards("9D").get(0))));
        assertThat(shown, not(hasItem(cards("KC").get(0))));
    }

    @Test
    void theViewShowsOtherHandsAsSizesOnly() throws Exception {
        PresidentBotView view = PresidentBotView.forSeat(table(), new PlayerId(1));

        assertThat(view.handSizes().get(new PlayerId(0)), is(2));
        assertThat(view.handSizes().get(new PlayerId(1)), is(3));
        assertThat(view.handSizes().get(new PlayerId(2)), is(3));
    }

    @Test
    void theViewShowsThePublicTrickAndTheSeatsActions() throws Exception {
        PresidentBotView view = PresidentBotView.forSeat(table(), new PlayerId(1));

        assertThat(view.phase(), is(Phase.PLAYING));
        assertThat(view.currentSeat(), is(new PlayerId(1)));
        assertThat(view.trick().top().cards(), is(cards("5H")));
        assertThat(view.actions(), hasItems(Action.PLAY, Action.PASS));
        assertThat(view.legalPlays().isEmpty(), is(false));
    }

    @Test
    void aSeatWhoseTurnItIsNotHasNoActionAndNoLegalPlay() throws Exception {
        PresidentBotView view = PresidentBotView.forSeat(table(), new PlayerId(2));

        assertThat(view.actions(), is(empty()));
        assertThat(view.legalPlays(), is(empty()));
    }

    @Test
    void theViewCarriesTheVersionAndTheRound() throws Exception {
        President game = table();

        PresidentBotView view = PresidentBotView.forSeat(game, new PlayerId(1));

        assertThat(view.version(), is(game.version()));
        assertThat(view.round(), is(1));
        assertThat(view.options(), is(game.options()));
    }
}
