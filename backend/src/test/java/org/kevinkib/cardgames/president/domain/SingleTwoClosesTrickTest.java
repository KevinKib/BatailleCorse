package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * Owner feedback of 2026-10-18 on the 2s that cut: the cards played must match the number of cards on the table. n twos
 * on n cards beat them and close the trick, whatever n; a lone 2 no longer closes a pair or a triple; a lone 2 led
 * still closes its trick. Under the equal-play binding ("X or nothing") a 2 is playable only when 2 is the bound rank.
 * The option {@code reducedCut} lets one 2 less close a pair or a triple.
 */
class SingleTwoClosesTrickTest {

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static Optional<TrickWon> closed(List<PresidentEvent> events) {
        return events.stream().filter(e -> e instanceof TrickWon).map(e -> (TrickWon) e).findFirst();
    }

    @Test
    void aSingleTwoOnASingleClosesTheTrickWithTheClassicDefaults() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults())
                .withHand("9S 3C").withHand("2S 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S"));
        Optional<TrickWon> won = closed(game.play(seat(1), cards("2S")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.trick().plays().isEmpty(), is(true));
    }

    @Test
    void aPairOfTwosOnAPairClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 3C").withHand("2S 2H 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S 9H"));
        Optional<TrickWon> won = closed(game.play(seat(1), cards("2S 2H")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void aTripleOfTwosOnATripleClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 9D 3C").withHand("2S 2H 2D 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S 9H 9D"));
        Optional<TrickWon> won = closed(game.play(seat(1), cards("2S 2H 2D")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void aLoneTwoCannotCloseAPairOrATripleAnyMore() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 3C").withHand("2S 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S 9H"));
        int version = game.version();

        assertThrows(PlayDoesNotBeatException.class, () -> game.play(seat(1), cards("2S")));
        assertThat(game.version(), is(version));
        assertThat(game.legalPlays(seat(1)).isEmpty(), is(true));
    }

    @Test
    void aSingleTwoNeverClosesATripleEvenWithTheReducedCut() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults().withReducedCut(true))
                .withHand("9S 9H 9D 3C").withHand("2S 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S 9H 9D"));

        assertThrows(PlayDoesNotBeatException.class, () -> game.play(seat(1), cards("2S")));
    }

    @Test
    void aLoneTwoLedClosesTheTrickAtOnce() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults())
                .withHand("2S 3C").withHand("4C 4D").withHand("KC KD 5C").withHand("QC QD 6C").build();
        Optional<TrickWon> won = closed(game.play(seat(0), cards("2S")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    @Test
    void aPairOfTwosLedClosesTheTrickAtOnceAndItsPlayerLeadsAgain() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H 3C").withHand("4C 4D").withHand("KC KD 5C").withHand("QC QD 6C").build();
        List<PresidentEvent> events = game.play(seat(0), cards("2S 2H"));
        assertThat(closed(events).map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    /** Open decision: a triple of 2s led is treated like a pair or a lone 2 led. */
    @Test
    void aTripleOfTwosLedClosesTheTrickAtOnce() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H 2D 3C").withHand("4C 4D").withHand("KC KD 5C").withHand("QC QD 6C").build();
        List<PresidentEvent> events = game.play(seat(0), cards("2S 2H 2D"));
        assertThat(closed(events).map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    @Test
    void aPairOfTwosLedThatEmptiesTheHandStillClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H").withHand("4C 4D").withHand("KC KD 5C").withHand("QC QD 6C").build();
        List<PresidentEvent> events = game.play(seat(0), cards("2S 2H"));
        assertThat(closed(events).map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void underTheEqualPlayBindingATwoIsNotPlayable() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("2S KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.play(seat(1), cards("9H"));
        int version = game.version();

        assertThrows(PlayDoesNotBeatException.class, () -> game.play(seat(2), cards("2S")));
        assertThat(game.version(), is(version));
        assertThat(game.legalPlays(seat(2)).isEmpty(), is(true));
    }

    @Test
    void aTwoIsNotPlayableAfterAChainOfSingles() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 5C").withHand("2S QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.play(seat(1), cards("9H"));
        game.play(seat(2), cards("9D"));
        // the chain of 9s binds D to a 9 or a pass
        assertThrows(PlayDoesNotBeatException.class, () -> game.play(seat(3), cards("2S")));
    }

    @Test
    void aSingleTwoAsTheLastCardIsAForbiddenFinishThatStillClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("5C 4C").withHand("2S").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.pass(seat(1));
        List<PresidentEvent> events = game.play(seat(2), cards("2S"));
        assertThat(events.stream().anyMatch(e -> e instanceof PlayerOut o && o.forbidden()), is(true));
        // the 2 closes the trick; C is out, so the next seat that still holds cards leads
        assertThat(closed(events).map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(3)));
        assertThat(game.trick().plays().isEmpty(), is(true));
    }

    @Test
    void aForbiddenQuadOfTwosPlayedAtOnceClosesTheTrickAsAQuad() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S 2H 2D 2C").withHand("4C 4D").withHand("5C 6C").build();
        List<PresidentEvent> events = game.play(seat(0), cards("2S 2H 2D 2C"));
        assertThat(events.stream().anyMatch(e -> e instanceof PlayerOut o && o.forbidden()), is(true));
        assertThat(closed(events).map(TrickWon::reason), is(Optional.of(TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    // ----- the reducedCut option -----

    @Test
    void withTheReducedCutOneTwoClosesAPair() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults().withReducedCut(true))
                .withHand("9S 9H 3C").withHand("2S 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S 9H"));

        Optional<TrickWon> won = closed(game.play(seat(1), cards("2S")));

        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void withTheReducedCutTwoTwosCloseATriple() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults().withReducedCut(true))
                .withHand("9S 9H 9D 3C").withHand("2S 2H 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S 9H 9D"));

        Optional<TrickWon> won = closed(game.play(seat(1), cards("2S 2H")));

        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void withTheReducedCutATwoIsStillNotPlayableUnderTheBinding() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults().withReducedCut(true))
                .withHand("9S 3C").withHand("9H 4C").withHand("2S KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.play(seat(1), cards("9H"));

        assertThrows(PlayDoesNotBeatException.class, () -> game.play(seat(2), cards("2S")));
        assertThat(game.legalPlays(seat(2)).isEmpty(), is(true));
    }
}
