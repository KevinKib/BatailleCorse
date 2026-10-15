package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.trick.MustLeadException;
import org.kevinkib.cardgames.president.domain.trick.TrickView;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.codes;

class TrickTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    private static List<PresidentEvent> play(President game, int seat, String played) throws Exception {
        return game.play(p(seat), cards(played));
    }

    private static List<PresidentEvent> pass(President game, int seat) throws Exception {
        return game.pass(p(seat));
    }

    /** Four players, nobody can go out during these scenarios. */
    private static PresidentBuilder fourPlayers() {
        return aPresident().withHand("3C 8C 8D").withHand("4C 9C 9D").withHand("5C JC JD").withHand("6C QC QD")
                .leader(0);
    }

    @Test
    void theLeaderMayPlayAnyCombinationAndTheNextPlayerMustMatchItsCount() throws Exception {
        President game = aPresident().withHand("5C 5D 9C").withHand("6C 7C 7D").withHand("8C 8D KC")
                .leader(0).build();

        play(game, 0, "5C 5D");

        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "7C"));
        play(game, 1, "7C 7D");
        assertThat(game.currentSeat(), is(p(2)));
    }

    @Test
    void theLeaderCannotPass() {
        President game = fourPlayers().build();

        assertThrows(MustLeadException.class, () -> pass(game, 0));
        assertThat(game.currentSeat(), is(p(0)));
    }

    @Test
    void aPlayThatDoesNotBeatTheTopIsRefusedAndNothingChanges() throws Exception {
        President game = fourPlayers().build();
        play(game, 0, "8C");
        int version = game.version();

        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "4C"));

        assertThat(game.version(), is(version));
        assertThat(game.currentSeat(), is(p(1)));
        assertThat(codes(game.hand(p(1))), is("4C 9C 9D"));
    }

    @Test
    void trickIsWonByTheLastPlayerWhenEveryoneElsePassed() throws Exception {
        President game = fourPlayers().build();

        play(game, 0, "3C");
        play(game, 1, "4C");
        play(game, 2, "5C");
        pass(game, 3);
        pass(game, 0);
        List<PresidentEvent> events = pass(game, 1);

        assertThat(events, hasItem(new TrickWon(p(2), TrickEndReason.ALL_PASSED)));
        assertThat(game.currentSeat(), is(p(2)));
        TrickView fresh = game.trick();
        assertThat(fresh.plays(), is(empty()));
        assertThat(fresh.leader(), is(p(2)));
        assertThat(fresh.top(), is(nullValue()));
        assertThat(codes(game.discard()), is("3C 4C 5C"));
    }

    @Test
    void theTrickIsNotOverWhileSomeoneHasNotAnsweredTheLastPlay() throws Exception {
        President game = fourPlayers().build();

        play(game, 0, "3C");
        pass(game, 1);
        pass(game, 2);

        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.trick().plays().size(), is(1));
        assertThat(game.trick().lastPlayer(), is(p(0)));
    }

    @Test
    void theTrickReadModelListsPlaysInOrder() throws Exception {
        President game = fourPlayers().build();

        play(game, 0, "3C");
        play(game, 1, "4C");

        TrickView view = game.trick();
        assertThat(view.leader(), is(p(0)));
        assertThat(view.lastPlayer(), is(p(1)));
        assertThat(codes(view.top().cards()), is("4C"));
        assertThat(view.plays().stream().map(t -> t.seat().id()).toList(), contains(0, 1));
    }

    @Test
    void afterPassReenterLetsAPasserPlayAgainWhenTheTurnComesBack() throws Exception {
        President game = fourPlayers().afterPass(AfterPass.REENTER).build();

        play(game, 0, "3C");
        pass(game, 1);
        play(game, 2, "5C");
        pass(game, 3);
        assertThat(game.currentSeat(), is(p(0)));
        pass(game, 0);

        assertThat(game.currentSeat(), is(p(1)));
        play(game, 1, "9C");
        assertThat(game.currentSeat(), is(p(2)));
    }

    @Test
    void afterPassOutEndsTheTrickAsSoonAsOnlyTheLastPlayerIsStillIn() throws Exception {
        President game = fourPlayers().afterPass(AfterPass.OUT).build();

        play(game, 0, "3C");
        pass(game, 1);
        play(game, 2, "5C");
        pass(game, 3);
        List<PresidentEvent> events = pass(game, 0);

        assertThat(events, hasItem(new TrickWon(p(2), TrickEndReason.ALL_PASSED)));
        assertThat(game.currentSeat(), is(p(2)));
        assertThat(game.trick().plays(), is(empty()));
    }

    @Test
    void afterPassOutAPasserIsSkippedForTheRestOfTheTrick() throws Exception {
        President game = fourPlayers().afterPass(AfterPass.OUT).build();

        play(game, 0, "3C");
        pass(game, 1);
        play(game, 2, "5C");
        play(game, 3, "6C");
        assertThat(game.currentSeat(), is(p(0)));
        pass(game, 0);

        assertThat(game.currentSeat(), is(p(2)));
        pass(game, 2);
        assertThat(game.currentSeat(), is(p(3)));
        assertThat(game.trick().plays(), is(empty()));
    }

    @Test
    void outPassersComeBackInTheNextTrick() throws Exception {
        President game = fourPlayers().afterPass(AfterPass.OUT).build();
        play(game, 0, "3C");
        pass(game, 1);
        pass(game, 2);
        pass(game, 3);
        assertThat(game.currentSeat(), is(p(0)));

        play(game, 0, "8C");
        play(game, 1, "9C");

        assertThat(game.currentSeat(), is(p(2)));
    }

    @Test
    void whenTheWinnerWentOutTheLeadGoesToTheNextSeatWithCards() throws Exception {
        President game = aPresident().withHand("9C").withHand("3C 4C 5C").withHand("3D 4D 5D")
                .withHand("3H 4H 5H").leader(0).build();

        play(game, 0, "9C");
        pass(game, 1);
        pass(game, 2);
        List<PresidentEvent> events = pass(game, 3);

        assertThat(events, hasItem(new TrickWon(p(1), TrickEndReason.ALL_PASSED)));
        assertThat(game.currentSeat(), is(p(1)));
        assertThat(game.trick().leader(), is(p(1)));
    }

    @Test
    void theLeadSkipsSeatsThatAreAlreadyOut() throws Exception {
        President game = aPresident().withHand("9C").withHand("3C 4C 5C").withHand("3D 4D 5D").withHand("JH")
                .leader(0).build();
        play(game, 0, "9C");
        pass(game, 1);
        pass(game, 2);
        pass(game, 3);
        assertThat(game.currentSeat(), is(p(1)));

        play(game, 1, "3C");
        pass(game, 2);
        play(game, 3, "JH");
        pass(game, 1);
        pass(game, 2);

        assertThat(game.finishingOrder(), contains(p(0), p(3)));
        assertThat(game.currentSeat(), is(p(1)));
        assertThat(game.trick().leader(), is(p(1)));
    }

    @Test
    void legalPlaysListTheCurrentSeatsOptionsAndNothingForOthers() throws Exception {
        President game = fourPlayers().build();

        assertThat(game.legalPlays(p(0)).stream().map(pl -> codes(pl.cards())).toList(),
                contains("3C", "8C", "8C 8D"));
        assertThat(game.legalPlays(p(1)), is(empty()));
        play(game, 0, "8C");
        assertThat(game.legalPlays(p(1)).stream().map(pl -> codes(pl.cards())).toList(), contains("9C"));
    }

    @Test
    void aPlayedCardLeavesTheHandAndAPassKeepsIt() throws Exception {
        President game = fourPlayers().build();

        play(game, 0, "3C");
        pass(game, 1);

        assertThat(codes(game.hand(p(0))), is("8C 8D"));
        assertThat(codes(game.hand(p(1))), is("4C 9C 9D"));
        assertThat(game.handSizes(), contains(2, 3, 3, 3));
    }
}
