package org.kevinkib.cardgames.kobo.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.EmptySlotException;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.kobo.domain.Phase;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventData;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cards.domain.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboGameActionsTest {

    private final KoboTable table = new KoboTable();

    private static Rank rank(String code) {
        return card(code).getRank();
    }

    @Test
    void aCommandBroadcastsOneStatePerSeatWithTheRightEventType() throws Exception {
        Kobo game = table.install(aKobo().withPlayers(3).build());

        table.actions.draw(game.getId(), seat(0));

        assertThat(table.messaging.seats, is(List.of(seat(0), seat(1), seat(2))));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess(), is(true));
        assertThat(first.getEventType(), is("DRAW"));
        assertThat(((KoboEventData) first.getEventData()).seat(), is(0));
        assertThat(((KoboDto) first.getState()).phase(), is("DECISION"));
    }

    @Test
    void theDrawnCardReachesTheDrawerOnly() throws Exception {
        Kobo game = table.install(aKobo().withPlayers(2).withDrawPile("9S", "2C").build());

        table.actions.draw(game.getId(), seat(0));

        KoboDto mine = (KoboDto) table.messaging.payloads.get(0).getState();
        KoboDto theirs = (KoboDto) table.messaging.payloads.get(1).getState();
        assertThat(mine.drawnCard(), notNullValue());
        assertThat(theirs.drawnCard(), nullValue());
        assertThat(theirs.hasDrawn(), is(true));
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() {
        Kobo game = table.install(aKobo().withPlayers(2).build());

        assertThrows(NotPlayersTurnException.class, () -> table.actions.draw(game.getId(), seat(1)));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void peekIsBroadcastWithoutTheCardAndRevealedToThePeekerOnly() throws Exception {
        Kobo game = table.install(aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.DECISION).withDrawn("9H").build());
        table.actions.discardDrawn(game.getId(), seat(0));
        table.messaging.clear();

        table.actions.peek(game.getId(), seat(0), ref(1, 3));

        Response toPeeker = table.messaging.payloads.get(0);
        Response toOther = table.messaging.payloads.get(1);
        assertThat(toPeeker.getEventType(), is("PEEK"));
        assertThat(((KoboEventData) toPeeker.getEventData()).card(), nullValue());
        assertThat(((KoboDto) toPeeker.getState()).reveal(), notNullValue());
        assertThat(((KoboDto) toOther.getState()).reveal(), nullValue());
    }

    @Test
    void matchCarriesTheCardOnlyWhenItSucceeds() throws Exception {
        Kobo game = table.install(aKobo().withTableau("7S", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("7H").build());

        table.actions.match(game.getId(), seat(0), ref(0, 1), null, rank("7H"), null);
        KoboEventData failed = (KoboEventData) table.messaging.payloads.get(0).getEventData();
        table.messaging.clear();
        table.actions.match(game.getId(), seat(0), ref(0, 0), null, rank("7H"), null);
        KoboEventData succeeded = (KoboEventData) table.messaging.payloads.get(0).getEventData();

        assertThat(failed.success(), is(false));
        assertThat(failed.card(), nullValue());
        assertThat(succeeded.success(), is(true));
        assertThat(succeeded.card().name().contains("7") || succeeded.card().name().contains("SEVEN"), is(true));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("MATCH"));
    }

    @Test
    void aCommandClosingTheRoundIsBroadcastAsRoundEndAndTheGameEndAsGameOver() throws Exception {
        Kobo game = table.install(aKobo().withTableau("AH", "KH", "KD", "AD").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").withDrawPile("2D", "3D", "4D").withTotals(0, 100 - 20)
                .inPhase(Phase.TURN_END).currentSeat(1).build());
        table.actions.announceKobo(game.getId(), seat(1));
        table.actions.draw(game.getId(), seat(0));
        table.actions.discardDrawn(game.getId(), seat(0));
        table.messaging.clear();

        table.actions.endTurn(game.getId(), seat(0));

        // announcer (33 points) is not the lowest: +20, so 80 + 33 + 20 = 133 -> game over
        assertThat(table.messaging.payloads.get(0).getEventType(), is("GAME_OVER"));
        assertThat(((KoboEventData) table.messaging.payloads.get(0).getEventData()).action(), is("END_TURN"));
        assertThat(game.isFinished(), is(true));

        Kobo round = table.install(aKobo().withTableau("AH", "KH", "KD", "AD").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").withDrawPile("2D", "3D", "4D").inPhase(Phase.TURN_END).currentSeat(1).build());
        table.actions.announceKobo(round.getId(), seat(1));
        table.actions.draw(round.getId(), seat(0));
        table.actions.discardDrawn(round.getId(), seat(0));
        table.messaging.clear();
        table.actions.endTurn(round.getId(), seat(0));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("ROUND_END"));
    }

    @Test
    void broadcastVersionsNeverGoBackwardsWhenSeatsRace() throws Exception {
        for (int run = 0; run < 50; run++) {
            table.messaging.clear();
            Kobo game = table.install(aKobo()
                    .withTableau("AH", "2H", "3H", "4H").withTableau("7D", "2C", "3C", "4C")
                    .withTableau("7C", "2D", "3D", "4D").withDiscard("7H").build());
            ExecutorService pool = Executors.newFixedThreadPool(3);
            CountDownLatch go = new CountDownLatch(1);
            List<Runnable> attempts = List.of(
                    () -> attempt(game, 1, ref(1, 0)),
                    () -> attempt(game, 2, ref(2, 0)),
                    () -> attempt(game, 0, ref(1, 0)));
            for (Runnable attempt : attempts) {
                pool.submit(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    attempt.run();
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            List<Integer> versionsForSeat0 = new ArrayList<>();
            synchronized (table.messaging.payloads) {
                for (int i = 0; i < table.messaging.payloads.size(); i++) {
                    if (table.messaging.seats.get(i).equals(seat(0))) {
                        versionsForSeat0.add(((KoboDto) table.messaging.payloads.get(i).getState()).version());
                    }
                }
            }
            for (int i = 1; i < versionsForSeat0.size(); i++) {
                assertThat(versionsForSeat0.get(i), greaterThanOrEqualTo(versionsForSeat0.get(i - 1)));
            }
        }
    }

    private void attempt(Kobo game, int actor, org.kevinkib.cardgames.kobo.domain.SlotRef target) {
        try {
            table.actions.match(game.getId(), new PlayerId(actor), target, actor == 0 ? 0 : null,
                    rank("7H"), null);
        } catch (Exception ignored) {
            // losing the race is expected
        }
    }

    @Test
    void emptySlotRejectionComesFromTheDomain() throws Exception {
        Kobo game = table.install(aKobo().withTableau("7S", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("7H").build());
        table.actions.match(game.getId(), seat(0), ref(0, 0), null, rank("7H"), null);

        assertThrows(EmptySlotException.class,
                () -> table.actions.match(game.getId(), seat(0), ref(0, 0), null, rank("7H"), null));
    }
}
