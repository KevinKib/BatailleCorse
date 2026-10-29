package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET_4;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;

/**
 * The three timers keyed on domain counters, never on the version: a choice or a slap bumps the version and must not
 * drop a valid timer; a voided round keeps its round and step numbers and must not keep the old timer.
 */
class KiloDeMerdeTimersTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);
    private static final Duration PAUSE = Duration.ofMillis(60_000);

    private KiloDeMerdeTable table;
    private ManualBotScheduler scheduler;
    private KiloDeMerdePassTimer passTimer;
    private KiloDeMerdeRaceTimeout raceTimeout;
    private KiloDeMerdeReadyTimeout roundPause;

    @BeforeEach
    void setUp() {
        KiloDeMerdeDeadlines deadlines = new KiloDeMerdeDeadlines(KiloDeMerdeTable.fixedClock(1_000_000), 15_000, 5_000, 60_000);
        table = new KiloDeMerdeTable(deadlines);
        scheduler = new ManualBotScheduler();
        passTimer = new KiloDeMerdePassTimer(table.actions, scheduler, deadlines);
        raceTimeout = new KiloDeMerdeRaceTimeout(table.actions, scheduler, deadlines);
        roundPause = new KiloDeMerdeReadyTimeout(table.actions, scheduler, deadlines);
        table.broadcaster.addListener(passTimer);
        table.broadcaster.addListener(raceTimeout);
        table.broadcaster.addListener(roundPause);
    }

    private void announce(KiloDeMerde game, String type) {
        table.broadcaster.broadcast(game, type, new EmptyEventData(), "");
    }

    // ---- pass timer ----

    @Test
    void nothingIsScheduledWhenThePassTimerIsOff() {
        KiloDeMerdeDeadlines off = KiloDeMerdeDeadlines.off();
        KiloDeMerdeTable plain = new KiloDeMerdeTable(off);
        ManualBotScheduler own = new ManualBotScheduler();
        plain.broadcaster.addListener(new KiloDeMerdePassTimer(plain.actions, own, off));
        KiloDeMerde game = plain.start(NO_SET);

        plain.broadcaster.broadcast(game, "START", new EmptyEventData(), "");

        assertThat(own.all()).isEmpty();
    }

    @Test
    void aStartedStepArmsOneTaskForTheRemainingTimeAndItPassesForTheSlowSeats() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        announce(game, "START");

        assertThat(scheduler.pending()).hasSize(1);
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(Duration.ofMillis(15_000));
        table.actions.choosePass(game.getId(), S0, card("QC"));
        assertThat(scheduler.pending()).hasSize(1); // a choice bumps the version, not the step: same task
        assertThat(scheduler.cancelledCount()).isZero();

        scheduler.runNext();

        assertThat(game.step()).isEqualTo(2);
        assertThat(scheduler.pending()).hasSize(1); // the next step is armed by the broadcast of the resolution
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(Duration.ofMillis(15_000));
    }

    @Test
    void aResolvedStepReplacesTheTask() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        announce(game, "START");
        ManualBotScheduler.Entry first = scheduler.pending().get(0);

        table.actions.choosePass(game.getId(), S0, card("QC"));
        table.actions.choosePass(game.getId(), S1, card("QD"));
        table.actions.choosePass(game.getId(), S2, card("JC"));

        assertThat(first.isCancelled()).isTrue();
        assertThat(scheduler.pending()).hasSize(1);
    }

    @Test
    void aRaceCancelsThePassTimerAndNothingIsArmedForPassing() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        announce(game, "START");
        ManualBotScheduler.Entry pass = scheduler.pending().get(0);

        table.actions.slap(game.getId(), S1);

        assertThat(pass.isCancelled()).isTrue();
        assertThat(scheduler.pending()).hasSize(1); // the race timeout
        assertThat(scheduler.pending().get(0).delay()).isEqualTo(Duration.ofMillis(5_000));
    }

    @Test
    void aVoidedRoundKeepsItsNumbersButNotTheOldTimer() {
        KiloDeMerde game = table.start(4, NO_SET_4, KiloDeMerdeTable.PILE);
        announce(game, "START");
        ManualBotScheduler.Entry beforeTheForfeit = scheduler.pending().get(0);

        game.forfeit(S1); // round 1, step 1 again, new deal, three seats
        announce(game, "FORFEIT");

        assertThat(game.round()).isEqualTo(1);
        assertThat(game.step()).isEqualTo(1);
        assertThat(beforeTheForfeit.isCancelled()).isTrue();
        assertThat(scheduler.pending()).hasSize(1);
    }

    @Test
    void aTaskThatFiresLateOnAStaleStepDoesNothing() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        announce(game, "START");
        ManualBotScheduler.Entry stale = scheduler.pending().get(0);
        table.actions.choosePass(game.getId(), S0, card("QC"));
        table.actions.choosePass(game.getId(), S1, card("QD"));
        table.actions.choosePass(game.getId(), S2, card("JC"));
        assertThat(game.step()).isEqualTo(2);

        stale.cancel();
        // a task the scheduler failed to cancel still carries the old key and is dropped by the use case
        table.actions.expirePassTimer(game.getId(), game, new KiloDeMerdeStepKey(1, 1, 3));

        assertThat(game.step()).isEqualTo(2);
        assertThat(game.hasChosen(S0)).isFalse();
    }

    @Test
    void aRoundEndAndAnEvictionCancelThePassTimer() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        announce(game, "START");
        ManualBotScheduler.Entry pass = scheduler.pending().get(0);

        passTimer.onEvicted(game.getId());

        assertThat(pass.isCancelled()).isTrue();

        announce(game, "START");
        ManualBotScheduler.Entry rearmed = scheduler.pending().get(0);
        table.actions.slap(game.getId(), S0); // false slap: the round is over
        assertThat(rearmed.isCancelled()).isTrue();
        assertThat(scheduler.pending().stream().filter(e -> e.delay().equals(Duration.ofMillis(15_000)))).isEmpty();
    }

    // ---- race timeout ----

    @Test
    void aRaceArmsOneTimeoutAndASlapOfAnotherSeatKeepsIt() throws Exception {
        KiloDeMerde game = table.start(4, ALL_SETS + " / 2S 2H 2D 2C", KiloDeMerdeTable.PILE);
        announce(game, "START");

        table.actions.slap(game.getId(), S1);
        ManualBotScheduler.Entry race = scheduler.pending().stream()
                .filter(e -> e.delay().equals(Duration.ofMillis(5_000))).findFirst().orElseThrow();
        table.actions.slap(game.getId(), S2);

        assertThat(race.isCancelled()).isFalse();
        assertThat(scheduler.pending().stream().filter(e -> e.delay().equals(Duration.ofMillis(5_000)))).hasSize(1);
    }

    @Test
    void theRaceTimeoutMakesTheAbsentSeatsDrawAndEndsTheRound() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        ManualBotScheduler.Entry race = scheduler.pending().get(0);

        race.run();

        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
        assertThat(game.kilos(S2)).isEqualTo(100);
    }

    @Test
    void aRaceThatEndedBeforeItsTimeoutCancelsIt() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        ManualBotScheduler.Entry race = scheduler.pending().get(0);

        table.actions.slap(game.getId(), S2);

        assertThat(race.isCancelled()).isTrue();
    }

    // ---- ready timeout ----

    @Test
    void aRoundEndSchedulesOneTaskAfterThePauseAndItDealsTheNextRound() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        table.actions.slap(game.getId(), S0);

        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
        ManualBotScheduler.Entry pause = scheduler.pending().stream().filter(e -> e.delay().equals(PAUSE)).findFirst()
                .orElseThrow();
        announce(game, "OPPONENT_DISCONNECTED");
        assertThat(scheduler.pending().stream().filter(e -> e.delay().equals(PAUSE))).hasSize(1); // still the same task
        table.messaging.clear();

        pause.run();

        assertThat(game.round()).isEqualTo(2);
        assertThat(game.phase()).isEqualTo(Phase.PASSING);
        assertThat(table.messaging.payloads.stream().map(r -> r.getEventType()).distinct())
                .containsExactly("READY", "ROUND_START");
    }

    @Test
    void aForfeitDuringTheRoundEndKeepsTheSamePauseAndAFinishedGameHasNone() throws Exception {
        KiloDeMerde game = table.start(4, NO_SET_4, KiloDeMerdeTable.PILE);
        table.actions.slap(game.getId(), S0);
        ManualBotScheduler.Entry pause = scheduler.pending().stream().filter(e -> e.delay().equals(PAUSE)).findFirst()
                .orElseThrow();

        game.forfeit(S1);
        announce(game, "FORFEIT");

        assertThat(pause.isCancelled()).isFalse();
        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);

        game.forfeit(S2);
        announce(game, "FORFEIT");

        assertThat(game.isFinished()).isTrue();
        assertThat(pause.isCancelled()).isTrue();
    }

    @Test
    void aStalePauseDoesNotDealAnotherRound() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.slap(game.getId(), S0);
        ManualBotScheduler.Entry pause = scheduler.pending().stream().filter(e -> e.delay().equals(PAUSE)).findFirst()
                .orElseThrow();
        table.nextRound(game, 1);
        assertThat(game.round()).isEqualTo(2);

        pause.cancel();
        table.nextRound(game, 1);

        assertThat(game.round()).isEqualTo(2);
        assertThat(game.step()).isEqualTo(1);
    }

    @Test
    void anEvictedGameCancelsItsPause() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.slap(game.getId(), S0);
        ManualBotScheduler.Entry pause = scheduler.pending().stream().filter(e -> e.delay().equals(PAUSE)).findFirst()
                .orElseThrow();

        roundPause.onEvicted(game.getId());

        assertThat(pause.isCancelled()).isTrue();
    }
}
