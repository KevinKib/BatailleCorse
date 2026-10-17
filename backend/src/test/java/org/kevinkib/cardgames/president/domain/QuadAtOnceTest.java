package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException;
import org.kevinkib.cardgames.president.domain.rules.EqualPlay;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * A quad played at once closes the trick like the four completed in succession (spec 3.1.1, owner decision O5):
 * the same reason {@code QUAD}, the same next leader, whatever the equal-play option says. The
 * {@code quadClears} option (V2, on by default) is exactly this behaviour: the tests here run with the default.
 */
class QuadAtOnceTest {

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static List<PresidentEvent> play(President game, int seat, String codes) throws Exception {
        return game.play(seat(seat), cards(codes));
    }

    @ParameterizedTest(name = "equalPlay {0}")
    @ValueSource(strings = {"on", "off"})
    void aQuadLedAtOnceClosesTheTrickAndItsPlayerLeadsAgain(String equalPlay) throws Exception {
        PresidentOptions options = PresidentOptions.defaults()
                .withEqualPlay(equalPlay.equals("on") ? EqualPlay.ON : EqualPlay.OFF);
        President game = aPresident().leader(0).options(options)
                .withHand("5S 5H 5D 5C 3C").withHand("KC 4C").withHand("QC 6C").build();

        List<PresidentEvent> events = play(game, 0, "5S 5H 5D 5C");

        assertThat(events, hasItem(new TrickWon(seat(0), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(0)));
        assertThat(game.trick().plays().isEmpty(), is(true));
        assertThat(game.discard().size(), is(4));
        assertThat(game.trick().chain(), is((Object) null));
    }

    @Test
    void aQuadIsNotAnswerableSinceItHasAlreadyClosedTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 5D 5C 3C").withHand("KS KH KD KC").withHand("QC 6C").build();

        play(game, 0, "5S 5H 5D 5C");

        // B never gets to answer with a higher quad: A leads the next trick.
        assertThrows(Exception.class, () -> play(game, 1, "KS KH KD KC"));
        assertThat(game.currentSeat(), is(seat(0)));
    }

    @Test
    void underAConstraintAQuadStillNeedsTheCountOfTheTopPlay() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5C 3C").withHand("KS KH KD KC 4C").withHand("QC 6C").build();
        play(game, 0, "5C");
        int version = game.version();

        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "KS KH KD KC"));

        assertThat(game.version(), is(version));
        assertThat(game.hand(seat(1)).size(), is(5));
    }

    @Test
    void aQuadThatEmptiesTheHandOfTheFirstSeatOutAgreesOnTheNextLeader() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 5D 5C").withHand("KC 4C").withHand("QC 6C").build();

        List<PresidentEvent> events = play(game, 0, "5S 5H 5D 5C");

        assertThat(events, hasItem(new PlayerOut(seat(0), 1)));
        // FIRST_OUT takes precedence when the quad takes 1st place; the next seat with cards leads, as for QUAD.
        assertThat(events, hasItem(new TrickWon(seat(1), TrickEndReason.FIRST_OUT)));
        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.finishingOrder(), contains(seat(0)));
    }

    @Test
    void aFirstOutQuadLeavesTheLeadToTheSeatRightAfterItsPlayer() throws Exception {
        President game = aPresident().leader(2)
                .withHand("3C 8C").withHand("4C 6C").withHand("5S 5H 5D 5C").withHand("QC 7C").build();

        List<PresidentEvent> events = play(game, 2, "5S 5H 5D 5C");

        assertThat(events, hasItem(new TrickWon(seat(3), TrickEndReason.FIRST_OUT)));
        assertThat(game.currentSeat(), is(seat(3)));
    }

    @Test
    void aQuadThatEmptiesALaterHandPassesTheLeadToTheNextSeatWithCards() throws Exception {
        President game = aPresident().leader(0)
                .withHand("3C 8C").withHand("5S 5H 5D 5C").withHand("QC 6C").build();
        play(game, 0, "3C");
        game.pass(seat(1));
        game.pass(seat(2));
        // A wins the trick and leads its last card: A is out first and B leads.
        play(game, 0, "8C");
        assertThat(game.finishingOrder(), contains(seat(0)));
        assertThat(game.currentSeat(), is(seat(1)));

        List<PresidentEvent> events = play(game, 1, "5S 5H 5D 5C");

        assertThat(events, hasItem(new PlayerOut(seat(1), 2)));
    }
}
