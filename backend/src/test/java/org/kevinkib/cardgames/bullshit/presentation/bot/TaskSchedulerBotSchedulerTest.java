package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSchedulerBotSchedulerTest {

    @Test
    void givenDelay_whenSchedule_thenTaskIsHandedToTheSpringSchedulerAtThatInstant() {
        TaskScheduler spring = mock(TaskScheduler.class);
        Runnable task = () -> { };
        Instant before = Instant.now();

        new TaskSchedulerBotScheduler(spring).schedule(Duration.ofMillis(1500), task);

        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(spring).schedule(any(Runnable.class), when.capture());
        long offset = Duration.between(before, when.getValue()).toMillis();
        assertThat(offset, greaterThanOrEqualTo(1500L));
        assertThat(offset, lessThanOrEqualTo(3000L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenScheduledTask_whenCancel_thenTheFutureIsCancelled() {
        TaskScheduler spring = mock(TaskScheduler.class);
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        when(spring.schedule(any(Runnable.class), any(Instant.class))).thenReturn((ScheduledFuture) future);

        BotScheduler.BotTask handle = new TaskSchedulerBotScheduler(spring).schedule(Duration.ofSeconds(1), () -> { });
        handle.cancel();

        verify(future).cancel(false);
    }

    @Test
    void givenRealScheduler_whenZeroDelay_thenTaskRuns() throws InterruptedException {
        ThreadPoolTaskScheduler real = new ThreadPoolTaskScheduler();
        real.setPoolSize(1);
        real.initialize();
        try {
            CountDownLatch ran = new CountDownLatch(1);

            new TaskSchedulerBotScheduler(real).schedule(Duration.ZERO, ran::countDown);

            assertThat(ran.await(5, TimeUnit.SECONDS), is(true));
        } finally {
            real.shutdown();
        }
    }
}
