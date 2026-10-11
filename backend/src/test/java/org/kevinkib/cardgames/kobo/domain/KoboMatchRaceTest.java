package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.domain.tableau.StaleSlotException;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kobo.domain.event.KoboEvent;
import org.kevinkib.cards.domain.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.match;

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
    void sameCardRaceWithoutExpectedRevision_isRejectedAsValidationForEveryone() throws Exception {
        Kobo kobo = aKobo()
                .withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                .withTableau("AD", "2D", "3D", "4D").withDiscard("7H").withDrawPile("8C", "9C", "10C").build();
        int cards = kobo.cardsInPlay();

        List<Throwable> outcomes = race(List.of(
                () -> kobo.matchDiscard(seat(0), ref(1, 0), 0, SEVEN, null),
                () -> kobo.matchDiscard(seat(2), ref(1, 0), 0, SEVEN, null)));

        assertThat(outcomes.stream().allMatch(t -> t instanceof IllegalArgumentException), is(true));
        assertThat(kobo.version(), is(0));
        assertThat(kobo.cardsInPlay(), is(cards));
        assertThat(kobo.handSize(seat(1)), is(4));
    }

    @Test
    void twoDifferentCardsOfTheSameRank_bothSucceed() throws Exception {
        Kobo kobo = aKobo().withTableau("7S", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                .withDiscard("7H").build();

        List<Throwable> outcomes = race(List.of(
                () -> match(kobo, seat(0), ref(0, 0), null, SEVEN),
                () -> match(kobo, seat(1), ref(1, 0), null, SEVEN)));

        assertThat(outcomes.stream().allMatch(t -> t == null), is(true));
        assertThat(kobo.discardSize(), is(3));
    }

    @Test
    void matchArrivingAfterTheTopMoved_isStaleAndNotPenalised() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                .withDiscard("7H").withDrawPile("8C", "9C").inPhase(Phase.DECISION).withDrawn("5S").build();
        int cards = kobo.cardsInPlay();

        kobo.discardDrawn(seat(0));
        int version = kobo.version();
        assertThrows(StaleDiscardException.class, () -> match(kobo, seat(1), ref(1, 0), null, SEVEN));

        assertThat(kobo.discardTop().get(), is(card("5S")));
        assertThat(kobo.version(), is(version));
        assertThat(kobo.cardsInPlay(), is(cards));
        assertThat(kobo.drawPileSize(), is(2));
        assertThat(kobo.handSize(seat(1)), is(4));
        assertThat(kobo.slots(seat(1)).size(), is(4));
    }

    @Test
    void matchArrivingBeforeTheTopMoves_succeedsAndTheDiscardStillGoesOnTop() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                .withDiscard("7H").withDrawPile("8C", "9C").inPhase(Phase.DECISION).withDrawn("5S").build();
        int cards = kobo.cardsInPlay();

        assertThat(match(kobo, seat(1), ref(1, 0), null, SEVEN) instanceof KoboEvent.MatchSucceeded, is(true));
        kobo.discardDrawn(seat(0));

        assertThat(kobo.discardTop().get(), is(card("5S")));
        assertThat(kobo.cardsInPlay(), is(cards));
        assertThat(kobo.drawPileSize(), is(2));
        assertThat(kobo.handSize(seat(1)), is(3));
        assertThat(kobo.slots(seat(1)).size(), is(4));
        assertThat(kobo.discardSize(), is(3));
    }
}
