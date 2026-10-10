package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.domain.tableau.StaleSlotException;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboMatchRaceTest {

    private static final Rank SEVEN = card("7H").getRank();

    /** Runs the tasks together (released by a latch) and returns the exception each one threw, or null. */
    private static List<Throwable> race(List<Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> f : futures) {
                outcomes.add(f.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameCardTargetedByThreePlayers_exactlyOneWinsAndTheOthersAreNotPenalised() throws Exception {
        for (int run = 0; run < 200; run++) {
            Kobo kobo = aKobo()
                    .withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                    .withTableau("AD", "2D", "3D", "4D").withTableau("AS", "2S", "3S", "4S")
                    .withDiscard("7H").withDrawPile("8C", "9C", "10C").build();
            int cards = kobo.cardsInPlay();
            int version = kobo.version();

            List<Throwable> outcomes = race(List.of(
                    () -> kobo.matchDiscard(seat(0), ref(1, 0), 0, SEVEN, 0),
                    () -> kobo.matchDiscard(seat(2), ref(1, 0), 0, SEVEN, 0),
                    () -> kobo.matchDiscard(seat(3), ref(1, 0), 0, SEVEN, 0)));

            assertThat(outcomes.stream().filter(t -> t == null).count(), is(1L));
            assertThat(outcomes.stream().filter(t -> t instanceof StaleSlotException).count(), is(2L));
            assertThat(kobo.cardsInPlay(), is(cards));
            assertThat(kobo.version(), is(version + 1));
            assertThat(kobo.drawPileSize(), is(3));
            assertThat(kobo.discardTop().get(), is(card("7D")));
        }
    }

    @Test
    void twoDifferentCardsOfTheSameRank_bothSucceed() throws Exception {
        Kobo kobo = aKobo().withTableau("7S", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                .withDiscard("7H").build();

        List<Throwable> outcomes = race(List.of(
                () -> kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN),
                () -> kobo.matchDiscard(seat(1), ref(1, 0), null, SEVEN)));

        assertThat(outcomes.stream().allMatch(t -> t == null), is(true));
        assertThat(kobo.discardSize(), is(3));
    }

    @Test
    void matchRacingADiscardOfAnotherRank_isEitherAMatchOrAnUnpenalisedStaleAttempt() throws Exception {
        for (int run = 0; run < 100; run++) {
            Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                    .withDiscard("7H").inPhase(Phase.DECISION).withDrawn("5S").build();
            int cards = kobo.cardsInPlay();

            List<Throwable> outcomes = race(List.of(
                    () -> kobo.discardDrawn(seat(0)),
                    () -> kobo.matchDiscard(seat(1), ref(1, 0), null, SEVEN)));

            assertThat(outcomes.get(0), is((Throwable) null));
            Throwable second = outcomes.get(1);
            assertThat(second == null || second instanceof StaleDiscardException, is(true));
            assertThat(kobo.cardsInPlay(), is(cards));
            assertThat(kobo.handSize(seat(1)), anyOf(is(3), is(4)));
            assertThat(kobo.slots(seat(1)).size(), is(4));
        }
    }
}
