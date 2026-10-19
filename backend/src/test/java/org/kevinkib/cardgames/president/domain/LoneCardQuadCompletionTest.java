package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.Played;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.CompletionClosedException;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The four of a kind is completed across plays by the cumulative count of one rank at the top of the trick, but
 * only by a play of exactly the cards still missing and never by a smaller group on a bigger one: three 5s close
 * a trick whose top is a lone 5 (in turn or out of turn), a lone 5 never finishes a triple of 5s.
 */
class LoneCardQuadCompletionTest {

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static List<PresidentEvent> play(President game, int seat, String codes) throws Exception {
        return game.play(seat(seat), cards(codes));
    }

    private static boolean offers(President game, int seat, FrenchRank rank, int count) {
        return game.legalPlays(seat(seat)).stream().anyMatch(p -> p.rank() == rank && p.count() == count);
    }

    @Test
    void threeCardsCompleteTheFourOnALoneCardAndTheirPlayerLeadsAgain() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("5H 5D 5C 4C").withHand("QC 6C").build();
        play(game, 0, "5S");

        assertThat(game.completion(seat(1)), containsInAnyOrder(cards("5H 5D 5C").toArray()));
        assertThat(game.availableActions(seat(1)).contains(Action.COMPLETE), is(true));
        List<PresidentEvent> events = play(game, 1, "5H 5D 5C");

        assertThat(events, hasItem(new TrickWon(seat(1), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(1)));
        assertThat(game.trick().plays().isEmpty(), is(true));
        assertThat(game.discard().size(), is(4));
    }

    @Test
    void theLoneCardMayHaveBeenPlayedAsAHigherSingleOverAnotherRank() throws Exception {
        President game = aPresident().leader(0)
                .withHand("4S 3C").withHand("5H 7C").withHand("5D 5C 5S 6C").build();
        play(game, 0, "4S");
        play(game, 1, "5H");

        List<PresidentEvent> events = play(game, 2, "5D 5C 5S");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void thePassesOfOtherPlayersDoNotCloseTheCompletion() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("8C 4C").withHand("5H 5D 5C 6C").build();
        play(game, 0, "5S");
        game.pass(seat(1));

        List<PresidentEvent> events = play(game, 2, "5H 5D 5C");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
    }

    @Test
    void theCompletionThatEmptiesTheHandTakesFirstPlaceAndClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("5H 5D 5C").withHand("QC 6C").build();
        play(game, 0, "5S");

        List<PresidentEvent> events = play(game, 1, "5H 5D 5C");

        assertThat(events, hasItem(new PlayerOut(seat(1), 1)));
        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.FIRST_OUT)));
    }

    @Test
    void theHolderOfTheThreeMissingCardsCompletesALoneCardOutOfTurn() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("4C 8C").withHand("5H 5D 5C 6C").build();
        play(game, 0, "5S");

        assertThat(game.completion(seat(2)), containsInAnyOrder(cards("5H 5D 5C").toArray()));
        assertThat(game.availableActions(seat(2)).contains(Action.COMPLETE), is(true));
        List<PresidentEvent> events = play(game, 2, "5H 5D 5C");

        assertThat(events, hasItem(new Played(seat(2), cards("5H 5D 5C"), true)));
        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void theOutOfTurnWindowOnALoneCardClosesWithTheNextPlay() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("6C 8C").withHand("7C 9C").withHand("5H 5D 5C 4C").build();
        play(game, 0, "5S");
        play(game, 1, "6C");
        int version = game.version();

        assertThat(game.completion(seat(3)).isEmpty(), is(true));
        assertThrows(CompletionClosedException.class, () -> play(game, 3, "5H 5D 5C"));

        assertThat(game.version(), is(version));
    }

    @Test
    void aPartOfTheMissingCardsOutOfTurnIsRefused() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("4C 8C").withHand("5H 5D 5C 6C").build();
        play(game, 0, "5S");

        assertThrows(CompletionClosedException.class, () -> play(game, 2, "5H 5D"));
    }

    @Test
    void aPairOfTheMissingCardsDoesNotCompleteALoneCard() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("5H 5D 9C 4C").withHand("QC 6C").build();
        play(game, 0, "5S");
        int version = game.version();

        assertThat(offers(game, 1, FrenchRank.FIVE, 2), is(false));
        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "5H 5D"));

        assertThat(game.version(), is(version));
    }

    @Test
    void threeCardsOfAnotherRankStillDoNotBeatALoneCard() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("6H 6D 6C 4C").withHand("QC 7C").build();
        play(game, 0, "5S");

        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "6H 6D 6C"));
    }

    @Test
    void aLoneCardNeverFinishesATripleOfTheSameRank() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 5D 3C").withHand("5C 9C").withHand("QC 6C").build();
        play(game, 0, "5S 5H 5D");
        int version = game.version();

        assertThat(offers(game, 1, FrenchRank.FIVE, 1), is(false));
        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "5C"));

        assertThat(game.version(), is(version));
        assertThat(game.hand(seat(1)).size(), is(2));
    }

    @Test
    void aLoneCardNeverFinishesATripleOutOfTurnEither() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 5D 3C").withHand("9C 4C").withHand("5C 6C").build();
        play(game, 0, "5S 5H 5D");

        assertThrows(NotPlayersTurnException.class, () -> play(game, 2, "5C"));
    }

    @Test
    void aLoneCardNeverFinishesATripleAfterAPass() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 5D 3C").withHand("9C 4C").withHand("5C 6C").build();
        play(game, 0, "5S 5H 5D");
        game.pass(seat(1));

        assertThat(offers(game, 2, FrenchRank.FIVE, 1), is(false));
        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 2, "5C"));
    }

    @Test
    void aPairOnAPairStillClosesAndASingleOnAPairStillDoesNot() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 3C").withHand("5D 5C 9C").withHand("QC 6C").build();
        play(game, 0, "5S 5H");

        assertThat(offers(game, 1, FrenchRank.FIVE, 1), is(false));
        assertThat(offers(game, 1, FrenchRank.FIVE, 2), is(true));
        assertThrows(PlayDoesNotBeatException.class, () -> play(game, 1, "5D"));
        List<PresidentEvent> events = play(game, 1, "5D 5C");

        assertThat(events, hasItem(new TrickWon(seat(1), TrickEndReason.QUAD)));
    }

    @Test
    void twoSinglesInSuccessionStillLetTheHolderOfTheLastTwoComplete() throws Exception {
        // 5S, then 5H (equal play): two plays at the top, the holder of the last two completes with a pair as before
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("5H 8C").withHand("5D 5C 6C").build();
        play(game, 0, "5S");
        play(game, 1, "5H");

        List<PresidentEvent> events = play(game, 2, "5D 5C");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
    }

    @Test
    void theEqualPlayBindingStillAsksForTheSameCountOnly() throws Exception {
        // 5S, 5H (equal play, C is bound): C may not play 5D 5C... but the completion of the two missing cards is
        // the existing in-turn completion; a lone 5D is the equal play, and the lone 5C then closes the four
        President game = aPresident().leader(0)
                .withHand("5S 3C").withHand("5H 8C").withHand("5D 9C 6C").withHand("5C 7C").build();
        play(game, 0, "5S");
        play(game, 1, "5H");
        play(game, 2, "5D");

        List<PresidentEvent> events = play(game, 3, "5C");

        assertThat(events, hasItem(new TrickWon(seat(3), TrickEndReason.QUAD)));
    }

    @Test
    void theCompletionIsNeverACompulsoryLegalPlay() throws Exception {
        PresidentOptions mustPlay = PresidentOptions.defaults().withMustPlay(true);
        President game = aPresident().leader(0).options(mustPlay)
                .withHand("5S 3C").withHand("5H 5D 5C 4C").withHand("QC 6C").build();
        play(game, 0, "5S");

        assertThat(offers(game, 1, FrenchRank.FIVE, 3), is(false));
        assertThat(game.completion(seat(1)).size(), is(3));
    }
}
