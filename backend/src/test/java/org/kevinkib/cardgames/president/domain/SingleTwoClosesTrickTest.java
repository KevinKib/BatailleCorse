package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.rules.EqualPlay;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * Owner feedback on the Classic rules: "a 2 closes the trick as soon as it is played, from the first 2", with no chain
 * and no four of 2s needed. So Classic has {@code twoCuts = single}, and one 2 played alone closes the trick wherever
 * it is played: on any play, a play of 2s included, with or without equal play; the player of the 2 leads next.
 * The one exception is the equal-play binding, which a cut never answers (spec V4).
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
    void aSingleTwoOnAPairOfTwosClosesTheTrick() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults())
                .withHand("2S 2H 3C").withHand("2D 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("2S 2H"));
        Optional<TrickWon> won = closed(game.play(seat(1), cards("2D")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(1)));
    }

    @Test
    void theSingleTwoOnAPairOfTwosDoesNotNeedEqualPlay() throws Exception {
        President game = aPresident().leader(0)
                .options(PresidentOptions.defaults().withEqualPlay(EqualPlay.OFF))
                .withHand("2S 2H 3C").withHand("2D 4C").withHand("KC KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("2S 2H"));
        assertThat(closed(game.play(seat(1), cards("2D"))).isPresent(), is(true));
    }

    @Test
    void aSingleTwoLedClosesTheTrickAtOnce() throws Exception {
        President game = aPresident().leader(0).options(PresidentOptions.defaults())
                .withHand("2S 3C").withHand("4C 4D").withHand("KC KD 5C").withHand("QC QD 6C").build();
        Optional<TrickWon> won = closed(game.play(seat(0), cards("2S")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    @Test
    void aSingleTwoClosesTheTrickEvenUnderTheEqualPlayBinding() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("2S KD 5C").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.play(seat(1), cards("9H"));
        Optional<TrickWon> won = closed(game.play(seat(2), cards("2S")));
        assertThat(won.map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void aSingleTwoClosesTheTrickAfterAChain() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 5C").withHand("2S QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.play(seat(1), cards("9H"));
        game.play(seat(2), cards("9D"));
        assertThat(closed(game.play(seat(3), cards("2S"))).isPresent(), is(true));
        assertThat(game.currentSeat(), is(seat(3)));
    }

    @Test
    void aSingleTwoAsTheLastCardIsAForbiddenFinishAndStillClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("2S").withHand("QC QD 6C").build();
        game.play(seat(0), cards("9S"));
        game.play(seat(1), cards("9H"));
        List<PresidentEvent> events = game.play(seat(2), cards("2S"));
        assertThat(events.stream().anyMatch(e -> e instanceof PlayerOut o && o.forbidden()), is(true));
        assertThat(closed(events).map(TrickWon::reason), is(Optional.of(TrickEndReason.CUT)));
        assertThat(game.currentSeat(), is(seat(3)));
    }
}
