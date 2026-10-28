package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;

/** The pause between two rounds: one task per round end, keyed on the version, dropped when stale. */
class NinetyNineRoundPauseTest {

    private static final Duration PAUSE = Duration.ofMillis(2500);
    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final NinetyNineTable table = new NinetyNineTable();
    private ManualBotScheduler scheduler;
    private NinetyNineRoundPause pause;

    @BeforeEach
    void setUp() {
        scheduler = new ManualBotScheduler();
        pause = new NinetyNineRoundPause(table.actions, scheduler, PAUSE);
        table.broadcaster.addListener(pause);
    }

    @Test
    void nothingIsScheduledWhileTheRoundIsBeingPlayed() throws Exception {
        NinetyNine game = table.atNinetyFour();

        table.broadcaster.broadcast(game, "START", new EmptyEventData(), "");

        assertThat(scheduler.all()).isEmpty();
    }

    @Test
    void aRoundEndSchedulesOneTaskAfterThePauseAndItDealsTheNextRound() throws Exception {
        NinetyNine game = table.beforeABust();

        table.actions.play(game.getId(), S0, card("KS"), null);

        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(PAUSE);
        table.messaging.clear();

        scheduler.runNext();

        assertThat(game.phase()).isEqualTo(Phase.PLAYING);
        assertThat(game.round()).isEqualTo(2);
        assertThat(table.messaging.payloads.get(0).getEventType()).isEqualTo("ROUND_START");
    }

    @Test
    void severalBroadcastsOfTheSameRoundEndKeepASingleTask() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.play(game.getId(), S0, card("KS"), null);

        table.broadcaster.broadcast(game, "OPPONENT_DISCONNECTED", new EmptyEventData(), "");
        table.broadcaster.broadcast(game, "OPPONENT_RECONNECTED", new EmptyEventData(), "");

        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.cancelledCount()).isZero();
    }

    @Test
    void aStateChangeBeforeTheTaskFiresMakesItStale() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.play(game.getId(), S0, card("KS"), null);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);
        game.forfeit(S1); // the version moved on without a broadcast reaching the pause
        int round = game.round();

        task.run();

        assertThat(game.round()).isEqualTo(round);
    }

    @Test
    void aForfeitThatKeepsTheRoundOverRearmsTheTaskOnTheNewVersion() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.play(game.getId(), S0, card("KS"), null);
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        game.forfeit(new PlayerId(2));
        table.broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "");

        assertThat(first.isCancelled()).isTrue();
        assertThat(game.isFinished()).isFalse(); // seats 0 and 1 are still in
        assertThat(scheduler.pending()).hasSize(1);
        scheduler.runNext();
        assertThat(game.round()).isEqualTo(2);
    }

    @Test
    void aFinishedGameSchedulesNothing() throws Exception {
        NinetyNine game = table.atNinetyFour();
        game.forfeit(S1);

        table.broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "");

        assertThat(scheduler.pending()).isEmpty();
    }

    @Test
    void anEvictedGameCancelsItsTask() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.play(game.getId(), S0, card("KS"), null);
        ManualBotScheduler.Entry task = scheduler.pending().get(0);

        pause.onEvicted(game.getId());

        assertThat(task.isCancelled()).isTrue();
    }

    /** A fresh table whose round pause deals through actions that fail {@code failures} times first. */
    private final class FlakyTable {
        final NinetyNineTable own = new NinetyNineTable();
        final ManualBotScheduler own_scheduler = new ManualBotScheduler();
        final int[] calls = {0};

        FlakyTable(int failures) {
            NinetyNineGameActions flaky = new NinetyNineGameActions(own.sessions, own.broadcaster) {
                @Override
                public void nextRound(org.kevinkib.cardgames.game.GameId id, int version)
                        throws org.kevinkib.cardgames.ninetynine.domain.NinetyNineException {
                    if (calls[0]++ < failures) {
                        throw new IllegalStateException("deal failed");
                    }
                    super.nextRound(id, version);
                }
            };
            own.broadcaster.addListener(new NinetyNineRoundPause(flaky, own_scheduler, PAUSE));
        }
    }

    @Test
    void aFailedDealIsRetriedSoTheGameDoesNotStayInRoundOver() throws Exception {
        FlakyTable flaky = new FlakyTable(2);
        NinetyNine game = flaky.own.beforeABust();
        flaky.own.actions.play(game.getId(), S0, card("KS"), null);

        flaky.own_scheduler.runNext();
        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
        assertThat(flaky.own_scheduler.pending()).hasSize(1);
        flaky.own_scheduler.runNext();
        assertThat(flaky.own_scheduler.pending()).hasSize(1);
        flaky.own_scheduler.runNext();

        assertThat(game.phase()).isEqualTo(Phase.PLAYING);
        assertThat(game.round()).isEqualTo(2);
        assertThat(flaky.own_scheduler.pending()).isEmpty();
    }

    @Test
    void aDealThatKeepsFailingIsTriedAtMostMaxAttemptsTimes() throws Exception {
        FlakyTable flaky = new FlakyTable(Integer.MAX_VALUE);
        NinetyNine game = flaky.own.beforeABust();
        flaky.own.actions.play(game.getId(), S0, card("KS"), null);

        while (!flaky.own_scheduler.pending().isEmpty()) {
            flaky.own_scheduler.runNext();
        }

        assertThat(flaky.calls[0]).isEqualTo(NinetyNineRoundPause.MAX_ATTEMPTS);
        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
    }
}
