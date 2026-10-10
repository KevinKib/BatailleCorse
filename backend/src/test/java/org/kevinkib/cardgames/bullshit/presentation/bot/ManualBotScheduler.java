package org.kevinkib.cardgames.bullshit.presentation.bot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Records scheduled tasks and runs them only when a test says so: no thread, no sleeping. */
public final class ManualBotScheduler implements BotScheduler {

    public static final class Entry implements BotTask {
        private final Duration delay;
        private final Runnable task;
        private boolean cancelled;
        private boolean ran;

        Entry(Duration delay, Runnable task) {
            this.delay = delay;
            this.task = task;
        }

        public Duration delay() {
            return delay;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean hasRun() {
            return ran;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        public void run() {
            if (cancelled || ran) {
                throw new IllegalStateException("Task is not runnable");
            }
            ran = true;
            task.run();
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public BotTask schedule(Duration delay, Runnable task) {
        Entry entry = new Entry(delay, task);
        entries.add(entry);
        return entry;
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    /** Tasks that are neither cancelled nor run yet. */
    public List<Entry> pending() {
        return entries.stream().filter(e -> !e.cancelled && !e.ran).toList();
    }

    public long cancelledCount() {
        return entries.stream().filter(e -> e.cancelled).count();
    }

    /** Runs the oldest pending task. */
    public void runNext() {
        pending().get(0).run();
    }

    /** Runs the tasks that are pending now, skipping any that get cancelled while this runs. */
    public void runAllPending() {
        for (Entry entry : pending()) {
            if (!entry.cancelled && !entry.ran) {
                entry.run();
            }
        }
    }
}
