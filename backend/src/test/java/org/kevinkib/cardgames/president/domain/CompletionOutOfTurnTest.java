package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PlayerOut;
import org.kevinkib.cardgames.president.domain.event.Played;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.CompletionClosedException;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.EqualPlay;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The completion of the four out of turn (spec 3.1.1) beyond the worked examples: what is refused and leaves the
 * game as it was, the window across passes, a passer under {@code afterPass = out}, a completion that empties the
 * hand, the actions on offer, the option off, and two commands racing for the same game.
 */
class CompletionOutOfTurnTest {

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static List<PresidentEvent> play(President game, int seat, String codes) throws Exception {
        return game.play(seat(seat), cards(codes));
    }

    /** A 9S, B 9H played; C (seat 2) holds the two missing 9s and is bound; D and A hold other cards. */
    private static President windowOpenForC() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("10H 6C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        return game;
    }

    @Test
    void anOutOfTurnPlayThatIsNotTheCompletionIsRefusedAndNothingChanges() throws Exception {
        President game = windowOpenForC();
        game.pass(seat(2));
        int version = game.version();
        List<Card> handOfA = game.hand(seat(0));
        List<Card> handOfC = game.hand(seat(2));

        // A is not to act and holds no completion; C holds it but offers one of the two missing 9s only.
        assertThrows(NotPlayersTurnException.class, () -> play(game, 0, "3C"));
        assertThrows(CompletionClosedException.class, () -> play(game, 2, "9D"));

        assertThat(game.version(), is(version));
        assertThat(game.hand(seat(0)), is(handOfA));
        assertThat(game.hand(seat(2)), is(handOfC));
        assertThat(game.trick().plays().size(), is(2));
        assertThat(game.currentSeat(), is(seat(3)));
        assertThat(game.completion(seat(2)), containsInAnyOrder(cards("9D 9C").toArray()));
    }

    @Test
    void aSeatThatIsNotTheHolderGetsNothingToComplete() throws Exception {
        President game = windowOpenForC();

        assertThat(game.completion(seat(0)), is(empty()));
        assertThat(game.completion(seat(1)), is(empty()));
        assertThat(game.completion(seat(3)), is(empty()));
        assertThat(game.completion(seat(2)), containsInAnyOrder(cards("9D 9C").toArray()));
    }

    @Test
    void theCompletionIsAcceptedAfterOneOrSeveralPassesAroundTheTable() throws Exception {
        President game = windowOpenForC();

        game.pass(seat(2));
        game.pass(seat(3));
        assertThat(game.completion(seat(2)), containsInAnyOrder(cards("9D 9C").toArray()));
        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(2)));
        assertThat(game.trick().plays().isEmpty(), is(true));
    }

    @Test
    void aCompletionAfterAPlayIsRefusedWhateverComesAfter() throws Exception {
        President game = windowOpenForC();
        game.pass(seat(2));
        play(game, 3, "10H");
        game.pass(seat(0));
        int version = game.version();

        assertThat(game.completion(seat(2)), is(empty()));
        assertThrows(CompletionClosedException.class, () -> play(game, 2, "9D 9C"));
        assertThat(game.version(), is(version));
        assertThat(game.hand(seat(2)).size(), is(3));
    }

    @Test
    void aPassersCompletionIsAcceptedUnderAfterPassOutToo() throws Exception {
        President game = aPresident().leader(0).afterPass(AfterPass.OUT)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("10H 6C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        game.pass(seat(2));
        game.pass(seat(3));
        assertThat(game.currentSeat(), is(seat(0)));
        assertThat(game.completion(seat(2)), containsInAnyOrder(cards("9D 9C").toArray()));

        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void aCompletionThatEmptiesTheHandOfTheFirstSeatOutAgreesOnTheNextLeader() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C").withHand("6C 7C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        game.pass(seat(2));
        game.pass(seat(3));

        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events, hasItem(new PlayerOut(seat(2), 1)));
        assertThat(events, hasItem(new TrickWon(seat(3), TrickEndReason.FIRST_OUT)));
        assertThat(game.currentSeat(), is(seat(3)));
        assertThat(game.finishingOrder(), contains(seat(2)));
    }

    @Test
    void aCompletionThatEmptiesTheHandOfALaterSeatOutPassesTheLeadToTheNextSeatWithCards() throws Exception {
        // Five seats: D (3) goes out first on the lead, E leads 5, A 9, B 9 (C is bound and passes), E passes.
        President game = aPresident().leader(3)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C").withHand("KC").withHand("5C 6C").build();
        play(game, 3, "KC");
        play(game, 4, "5C");
        play(game, 0, "9S");
        play(game, 1, "9H");
        game.pass(seat(2));
        game.pass(seat(4));
        assertThat(game.currentSeat(), is(seat(0)));

        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events, hasItem(new PlayerOut(seat(2), 2)));
        assertThat(events, hasItem(new TrickWon(seat(4), TrickEndReason.QUAD)));
        assertThat(game.currentSeat(), is(seat(4)));
        assertThat(game.finishingOrder(), contains(seat(3), seat(2)));
    }

    @Test
    void theEventTellsAnOutOfTurnCompletionFromAnInTurnPlay() throws Exception {
        President game = windowOpenForC();

        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events.get(0), is(new Played(seat(2), cards("9D 9C"), true)));

        President inTurn = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 5C").withHand("9C 6C").build();
        play(inTurn, 0, "9S");
        play(inTurn, 1, "9H");
        play(inTurn, 2, "9D");
        assertThat(play(inTurn, 3, "9C").get(0), is(new Played(seat(3), cards("9C"), false)));
    }

    @Test
    void theActionsOfAHolderIncludeCompleteInOrOutOfTurn() throws Exception {
        President game = windowOpenForC();

        // C is bound to a 9 and holds the completion: it may match, pass, or complete.
        assertThat(game.availableActions(seat(2)), is(List.of(Action.PLAY, Action.PASS, Action.COMPLETE)));
        game.pass(seat(2));
        // Out of turn, COMPLETE is the only action of C.
        assertThat(game.availableActions(seat(2)), is(List.of(Action.COMPLETE)));
        assertThat(game.availableActions(seat(3)), is(List.of(Action.PLAY, Action.PASS)));
        play(game, 3, "10H");
        assertThat(game.availableActions(seat(2)), is(empty()));
    }

    @Test
    void withEqualPlayOffNothingBindsAndNothingCanBeCompleted() throws Exception {
        PresidentOptions off = PresidentOptions.defaults().withEqualPlay(EqualPlay.OFF);
        President game = aPresident().leader(0).options(off)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("10H 6C").build();
        play(game, 0, "9S");

        assertThrows(org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException.class,
                () -> play(game, 1, "9H"));
        game.pass(seat(1));
        assertThat(game.completion(seat(2)), is(empty()));
        assertThrows(NotPlayersTurnException.class, () -> play(game, 3, "10H"));
    }

    @Test
    void aCurrentPlayerWhoHoldsTheMissingCardsMayCompleteInTurnWhateverTheCount() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("6C 7C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");

        // C is bound to a single 9, yet two cards are a completion.
        List<PresidentEvent> events = play(game, 2, "9D 9C");

        assertThat(events, hasItem(new TrickWon(seat(2), TrickEndReason.QUAD)));
    }

    @Test
    void theCurrentPlayersPlayAndTheCompletionSubmittedTogetherAreSerialisedAndOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 200; attempt++) {
                President game = windowOpenForC();
                game.pass(seat(2));
                int cardsBefore = PresidentDriver.cardsInGame(game).size();
                CountDownLatch go = new CountDownLatch(1);
                AtomicInteger accepted = new AtomicInteger();
                AtomicInteger refused = new AtomicInteger();
                Runnable current = () -> race(go, accepted, refused, () -> play(game, 3, "10H"));
                Runnable holder = () -> race(go, accepted, refused, () -> play(game, 2, "9D 9C"));
                var first = pool.submit(attempt % 2 == 0 ? current : holder);
                var second = pool.submit(attempt % 2 == 0 ? holder : current);
                go.countDown();
                first.get();
                second.get();

                assertThat("exactly one command wins (attempt " + attempt + ")", accepted.get(), is(1));
                assertThat(refused.get(), is(1));
                assertThat(PresidentDriver.cardsInGame(game).size(), is(cardsBefore));
                assertThat(PresidentDriver.wholeDeck(game, cardsBefore), is(true));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private interface Command {
        void run() throws Exception;
    }

    private static void race(CountDownLatch go, AtomicInteger accepted, AtomicInteger refused, Command command) {
        try {
            go.await();
            command.run();
            accepted.incrementAndGet();
        } catch (org.kevinkib.cardgames.president.domain.rules.PresidentException e) {
            refused.incrementAndGet();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
