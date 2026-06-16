package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

public class TaskSchedulerForfeitScheduler implements ForfeitScheduler {

    private final TaskScheduler taskScheduler;

    public TaskSchedulerForfeitScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public ScheduledForfeit schedule(Instant deadline, Runnable task) {
        ScheduledFuture<?> future = taskScheduler.schedule(task, deadline);
        return () -> future.cancel(false);
    }
}
