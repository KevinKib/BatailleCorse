package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.Played;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.CompletionClosedException;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * A pair alone at the top of the trick (one play) opens the completion window as a lone card does: the holder of the
 * other pair may play it at once, out of turn, and closes the four. A triple alone still does not (a lone card never
 * finishes a bigger group), and the window closes with the next play.
 */
class LonePairQuadCompletionTest {

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static List<PresidentEvent> play(President game, int seat, String codes) throws Exception {
        return game.play(seat(seat), cards(codes));
    }

    @Test
    void theHolderOfTheOtherPairCompletesALonePairOutOfTurn() throws Exception {
        President game = aPresident().leader(0)
                .withHand("4S 4H 3C").withHand("8C 9C").withHand("4D 4C 6C").build();
        play(game, 0, "4S 4H");

        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.completion(seat(2)), containsInAnyOrder(cards("4D 4C").toArray()));
        assertThat(game.availableActions(seat(2)).contains(Action.COMPLETE), is(true));
        List<PresidentEvent> events = play(game, 2, "4D 4C");

        assertThat(events, hasItem(new Played(seat(2), cards("4D 4C"), true)));
        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(2)));
        assertThat(game.trick().plays().isEmpty(), is(true));
    }

    @Test
    void thePassesOfOtherPlayersDoNotCloseTheWindowOnALonePair() throws Exception {
        President game = aPresident().leader(0)
                .withHand("4S 4H 3C").withHand("8C 9C").withHand("7C 7D").withHand("4D 4C 6C").build();
        play(game, 0, "4S 4H");
        game.pass(seat(1));

        List<PresidentEvent> events = play(game, 3, "4D 4C");

        assertThat(events, hasItem(new TrickWon(seat(3), TrickEndReason.QUAD)));
    }

    @Test
    void theWindowOnALonePairClosesWithTheNextPlay() throws Exception {
        President game = aPresident().leader(0)
                .withHand("4S 4H 3C").withHand("8C 8D 9C").withHand("7C 7D").withHand("4D 4C 6C").build();
        play(game, 0, "4S 4H");
        play(game, 1, "8C 8D");
        int version = game.version();

        assertThat(game.completion(seat(3)).isEmpty(), is(true));
        assertThrows(CompletionClosedException.class, () -> play(game, 3, "4D 4C"));
        assertThat(game.version(), is(version));
    }

    @Test
    void aLoneTripleStillOffersNoCompletionOfOneCard() throws Exception {
        President game = aPresident().leader(0)
                .withHand("4S 4H 4D 3C").withHand("8C 9C").withHand("4C 6C").build();
        play(game, 0, "4S 4H 4D");

        assertThat(game.completion(seat(2)).isEmpty(), is(true));
        assertThat(game.availableActions(seat(2)).contains(Action.COMPLETE), is(false));
    }
}
