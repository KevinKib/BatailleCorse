package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/** Adapter of the {@link BotScheduler} port on the Spring {@link TaskScheduler}. */
public class TaskSchedulerBotScheduler implements BotScheduler {

    private final TaskScheduler taskScheduler;

    public TaskSchedulerBotScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public BotTask schedule(Duration delay, Runnable task) {
        ScheduledFuture<?> future = taskScheduler.schedule(task, Instant.now().plus(delay));
        return () -> {
            if (future != null) {
                future.cancel(false);
            }
        };
    }
}
