package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.MustPlayException;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The trick variants of PR V2, from the spec catalogue: the 2 that closes the trick (V4) and the quad played at once
 * (V5), both fixed rules, then the forbidden finish (V7, with worked example 9 of 3.1.2) and compulsory play (V11).
 * Four players A, B, C, D are seats 0 to 3. A play that closes the trick with a lone 2 is a cut, so the forbidden
 * finishes that must not close it are made with a pair of 2s.
 */
class TrickVariantsTest {

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static List<PresidentEvent> play(President game, int seat, String codes) throws Exception {
        return game.play(seat(seat), cards(codes));
    }

    private static Optional<TrickWon> closed(List<PresidentEvent> events) {
        return events.stream().filter(e -> e instanceof TrickWon).map(e -> (TrickWon) e).findFirst();
    }

    // ----- the effect matrix through the aggregate: A leads the top, B answers -----

    /**
     * Rows: top led by A (which does not close the trick), B's candidate, how B's command is answered ({@code ok} or
     * the refusal) and why the trick closed ({@code NONE} when it did not). A and B keep a spare card so that nobody
     * goes out.
     */
    @ParameterizedTest(name = "{1} on {0} -> {2}, trick closed by {3}")
    @CsvSource(delimiter = '|', value = {
            "9S          | 2S          | ok                       | CUT",
            "9S 9H       | 2S          | ok                       | CUT",
            "9S 9H 9D    | 2S          | ok                       | CUT",
            "9S          | 2S 2H       | PlayDoesNotBeatException | NONE",
            "9S 9H       | 2S 2H       | ok                       | NONE",
            "9S 9H 9D    | 2S 2H       | PlayDoesNotBeatException | NONE",
            "9S          | JS          | ok                       | NONE",
    })
    void theEffectMatrix(String top, String candidate, String answer, String closedBy) throws Exception {
        President game = aPresident().leader(0)
                .withHand(top.trim() + " 3C").withHand(candidate.trim() + " 4C")
                .withHand("KC KD 5C").withHand("QC QD 6C").build();
        play(game, 0, top.trim());
        int version = game.version();

        String result;
        Optional<TrickWon> won = Optional.empty();
        try {
            won = closed(play(game, 1, candidate.trim()));
            result = "ok";
        } catch (PlayDoesNotBeatException e) {
            result = e.getClass().getSimpleName();
        }

        assertThat(result, is(answer.trim()));
        assertThat(won.map(w -> w.reason().name()).orElse("NONE"), is(closedBy.trim()));
        assertThat(game.version(), is(result.equals("ok") ? version + 1 : version));
        if (won.isPresent()) {
            assertThat(game.currentSeat(), is(seat(1)));
            assertThat(game.trick().plays().isEmpty(), is(true));
        }
    }

    // ----- V4: the 2 closes the trick -----

    @Test
    void aLoneTwoClosesTheTrickAndItsPlayerLeadsTheNextTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 3C").withHand("2S 4C").withHand("KC KD").withHand("QC 6C").build();
        play(game, 0, "9S 9H");

        List<PresidentEvent> events = play(game, 1, "2S");

        assertThat(events, hasItem(new TrickWon(seat(1), TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.discard().size(), is(3));
        assertThat(game.trick().plays().isEmpty(), is(true));
    }

    @Test
    void leadingALoneTwoWinsTheTrickAtOnce() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 3C").withHand("2H 4C").withHand("KC KD").withHand("QC 6C").build();

        List<PresidentEvent> events = play(game, 0, "2S");

        assertThat(events, hasItem(new TrickWon(seat(0), TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    @Test
    void aLoneTwoOnAPairOfTwosClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H 3C").withHand("2D 4C").withHand("KC KD").withHand("QC 6C").build();
        play(game, 0, "2S 2H");

        List<PresidentEvent> events = play(game, 1, "2D");

        assertThat(events, hasItem(new TrickWon(seat(1), TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void aPairOfTwosIsAnOrdinaryPlayThatDoesNotCloseTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 3C").withHand("2S 2H 4C").withHand("KC KD").withHand("QC 6C").build();
        play(game, 0, "9S 9H");

        List<PresidentEvent> events = play(game, 1, "2S 2H");

        assertThat(closed(events).isPresent(), is(false));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void aLoneTwoClosesTheTrickEvenUnderTheEqualPlayBinding() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("2S 5C").withHand("2H 6C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        assertThat(game.trick().constraint().name(), is("MATCH_OR_PASS"));

        // C is bound to a 9 or a pass, but a lone 2 always closes the trick
        assertThat(game.legalPlays(seat(2)).stream().map(p -> p.count() + ":" + p.rank()).toList().size(), is(1));
        List<PresidentEvent> events = play(game, 2, "2S");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void aLoneTwoClosesTheTrickAfterAChainOfEqualPlays() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 5C").withHand("2H 6C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        play(game, 2, "9D");

        List<PresidentEvent> events = play(game, 3, "2H");

        assertThat(events, hasItem(new TrickWon(seat(3), TrickEndReason.CUT)));
    }

    @Test
    void aLoneTwoOutOfTurnIsNotACompletion() throws Exception {
        // E holds the missing nines and a 2, but out of turn only a completion is accepted
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("5C 6C").withHand("7C 8C").withHand("9D 9C 2H").build();
        play(game, 0, "9S");
        play(game, 1, "9H");

        assertThrows(org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException.class,
                () -> play(game, 4, "2H"));
    }

    @Test
    void aLoneTwoThatEmptiesTheHandIsAForbiddenFinishAndDoesNotCloseTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 3C").withHand("2S").withHand("KC KD").withHand("QC 6C").build();
        play(game, 0, "9S 9H");

        List<PresidentEvent> events = play(game, 1, "2S");

        assertThat(events, hasItem(new PlayerOut(seat(1), 0, true)));
        assertThat(closed(events).isPresent(), is(false));
        assertThat(game.currentSeat(), is(seat(2)));
        assertThat(game.finishingOrder(), is(List.of()));
    }

    // ----- V5: the four closes the trick -----

    @Test
    void theFourCompletedInSuccessionClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 5C").withHand("9C 6C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        play(game, 2, "9D");

        List<PresidentEvent> events = play(game, 3, "9C");

        assertThat(events, hasItem(new TrickWon(seat(3), TrickEndReason.QUAD)));
    }

    // ----- V7: the forbidden finish -----

    /** Worked example 9 (spec 3.1.2): C goes out on 2s, is not placed 1st and closes nothing. */
    @Test
    void example9AForbiddenFinisherOutFirstDoesNotCloseTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("KS KH 3C").withHand("AS AH 4C").withHand("2S 2H").withHand("3H 3D 5C").build();
        play(game, 0, "KS KH");
        play(game, 1, "AS AH");

        List<PresidentEvent> events = play(game, 2, "2S 2H");

        assertThat(events, hasItem(new PlayerOut(seat(2), 0, true)));
        assertThat(closed(events).isPresent(), is(false));
        assertThat(game.finishingOrder(), is(List.of()));
        assertThat(game.forbiddenFinishers(), contains(seat(2)));
        assertThat(game.currentSeat(), is(seat(3)));
        assertThat(game.trick().plays().size(), is(3));

        // nothing but a lone 2 beats a pair of 2s: D, A and B pass, the trick closes on C's play
        game.pass(seat(3));
        game.pass(seat(0));
        List<PresidentEvent> end = game.pass(seat(1));

        assertThat(end, hasItem(new TrickWon(seat(3), TrickEndReason.ALL_PASSED)));
        assertThat(game.currentSeat(), is(seat(3)));
        assertThat(game.phase(), is(Phase.PLAYING));
    }

    /** The first finisher after a forbidden one takes 1st place and closes the trick at once. */
    @Test
    void theFirstFinisherAfterAForbiddenOneTakesFirstPlaceAndClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("KS KH 3C").withHand("AS AH 4C").withHand("2S 2H").withHand("3H 3D").build();
        play(game, 0, "KS KH");
        play(game, 1, "AS AH");
        play(game, 2, "2S 2H");
        game.pass(seat(3));
        game.pass(seat(0));
        game.pass(seat(1));
        // D leads its last two cards: D is out first of the normal finishers
        List<PresidentEvent> events = play(game, 3, "3H 3D");

        assertThat(events, hasItem(new PlayerOut(seat(3), 1)));
        assertThat(events, hasItem(new TrickWon(seat(0), TrickEndReason.FIRST_OUT)));
        assertThat(game.finishingOrder(), contains(seat(3)));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    @Test
    void twoForbiddenFinishersTheLaterOneIsLowerAndBothFollowTheOthers() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H").withHand("2D").withHand("3C").withHand("4C").build();
        play(game, 0, "2S 2H");                    // A out, forbidden, first; the trick stays open
        List<PresidentEvent> second = play(game, 1, "2D");   // B out, forbidden, second: no cut, the trick stays open
        assertThat(second, hasItem(new PlayerOut(seat(1), 0, true)));
        assertThat(closed(second).isPresent(), is(false));
        assertThat(game.forbiddenFinishers(), contains(seat(0), seat(1)));
        game.pass(seat(2));
        game.pass(seat(3));                                  // nobody holds a 2: C leads

        List<PresidentEvent> last = play(game, 2, "3C");   // C goes out normally: 1st place, D keeps its card

        assertThat(last, hasItem(new PlayerOut(seat(2), 1)));
        assertThat(game.phase(), is(Phase.ROUND_OVER));
        // C 1st, D (the last one holding cards) above the forbidden finishers, A above B who went out later
        assertThat(game.finishingOrder(), contains(seat(2), seat(3), seat(0), seat(1)));
    }

    @Test
    void aQuadOfTwosPlayedAtOnceThatEmptiesTheHandIsAForbiddenFinishThatDoesNotCloseTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H 2D 2C").withHand("4C 4D").withHand("5C 6C").build();

        List<PresidentEvent> events = play(game, 0, "2S 2H 2D 2C");

        assertThat(events, hasItem(new PlayerOut(seat(0), 0, true)));
        assertThat(closed(events).isPresent(), is(false));
        assertThat(game.currentSeat(), is(seat(1)));
    }


    @Test
    void aTwoPlayedWithOtherCardsLeftInTheHandIsNotAFinish() throws Exception {
        President game = aPresident().leader(0)
                .withHand("KS 3C").withHand("AS 4C").withHand("2S 5C").withHand("3H 6C").build();
        play(game, 0, "KS");
        play(game, 1, "AS");

        List<PresidentEvent> events = play(game, 2, "2S");

        assertThat(events.stream().anyMatch(e -> e instanceof PlayerOut), is(false));
    }

    @Test
    void aForbiddenFinisherLeftOnTheLastRoundPlacesTheLastHolderAboveThem() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S").withHand("3C 4C").withHand("5C 6C").build();
        play(game, 0, "2S");                       // A leads its last card, a 2: forbidden, the trick stays open
        game.pass(seat(1));
        game.pass(seat(2));                        // nobody holds a 2: the trick ends and B leads
        play(game, 1, "3C");
        game.pass(seat(2));

        List<PresidentEvent> events = play(game, 1, "4C");

        assertThat(events, hasItem(new PlayerOut(seat(1), 1)));
        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.finishingOrder(), contains(seat(1), seat(2), seat(0)));
    }

    // ----- V11: compulsory play -----

    private static PresidentOptions mustPlay() {
        return PresidentOptions.defaults().withMustPlay(true);
    }

    @Test
    void withMustPlayPassingIsRefusedWhileALegalPlayExists() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("5S 3C").withHand("9H 4C").withHand("KC KD").withHand("QC 6D").build();
        play(game, 0, "5S");
        int version = game.version();

        assertThrows(MustPlayException.class, () -> game.pass(seat(1)));

        assertThat(game.version(), is(version));
        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.availableActions(seat(1)), is(List.of(Action.PLAY)));
    }

    @Test
    void withMustPlayAPassIsAllowedWhenNothingCanPlay() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("KS 3C").withHand("5H 4C").withHand("KC KD").withHand("QC 6D").build();
        play(game, 0, "KS");

        assertThat(game.availableActions(seat(1)), is(List.of(Action.PASS)));
        game.pass(seat(1));

        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void withMustPlayOffTheSameHandMayPass() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("9H 4C").withHand("KC KD").withHand("QC 6D").build();
        play(game, 0, "5S");

        assertThat(game.availableActions(seat(1)), is(List.of(Action.PLAY, Action.PASS)));
        game.pass(seat(1));
    }

    @Test
    void anEqualPlayCountsAsALegalPlayForMustPlay() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("9S 3C").withHand("9H 4C").withHand("KC KD").withHand("QC 6D").build();
        play(game, 0, "9S");

        assertThrows(MustPlayException.class, () -> game.pass(seat(1)));
    }

    @Test
    void aLoneTwoCountsAsALegalPlayForMustPlay() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("9S 9H 3C").withHand("2H 4C").withHand("KC KD").withHand("QC 6D").build();
        play(game, 0, "9S 9H");

        assertThrows(MustPlayException.class, () -> game.pass(seat(1)));
    }

    @Test
    void underTheBindingOnlyAMatchingPlayOrALoneTwoCountsForMustPlay() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("9S 3C").withHand("9H 4C").withHand("10C KC").withHand("QC 6D").build();
        play(game, 0, "9S");
        play(game, 1, "9H");

        // C holds a 10 and a king, both higher, but the binding allows only a 9: passing is allowed
        assertThat(game.availableActions(seat(2)), is(List.of(Action.PASS)));
        game.pass(seat(2));

        President withTwo = aPresident().leader(0).options(mustPlay())
                .withHand("9S 3C").withHand("9H 4C").withHand("2C KC").withHand("QC 6D").build();
        play(withTwo, 0, "9S");
        play(withTwo, 1, "9H");
        assertThrows(MustPlayException.class, () -> withTwo.pass(seat(2)));
    }

    @Test
    void aLegalPlayThatWouldBeAForbiddenFinishStillCountsForMustPlay() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("KS 3C").withHand("2H").withHand("KC KD").withHand("QC 6D").build();
        play(game, 0, "KS");

        assertThrows(MustPlayException.class, () -> game.pass(seat(1)));
    }

    @Test
    void aCompletionIsNeverCompulsoryAHolderOutOfTurnOnlyHasTheOptionToComplete() throws Exception {
        // five players: E holds the two missing 9s but is not on turn when B's equal 9 binds C
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("9S 3C").withHand("9H 4C").withHand("5C 6C").withHand("7C 8C").withHand("9D 9C JC").build();
        play(game, 0, "9S");
        play(game, 1, "9H");

        assertThat(game.availableActions(seat(4)), is(List.of(Action.COMPLETE)));
        // C has no 9: bound, nothing legal, passes; the completion stays open and optional for E
        game.pass(seat(2));
        game.pass(seat(3));
        assertThat(game.availableActions(seat(4)), is(List.of(Action.PLAY, Action.COMPLETE)));
    }

    @Test
    void theHolderOnTurnMustPlayButMayChooseTheCompletionInsteadOfASingleNine() throws Exception {
        President game = aPresident().leader(0).options(mustPlay())
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("7C 8C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");

        assertThrows(MustPlayException.class, () -> game.pass(seat(2)));
        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
    }
}
