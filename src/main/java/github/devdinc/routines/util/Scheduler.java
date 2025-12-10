package github.devdinc.routines.util;

import java.time.Duration;

/**
 * A functional interface for scheduling tasks.
 */
@FunctionalInterface
public interface Scheduler {
    /**
     * Schedules a task to be executed after a certain delay and then periodically.
     *
     * @param task    The task to be scheduled.
     * @param after   The initial delay before the first execution.
     * @param every   The period between subsequent executions.
     * @param context The context for the task.
     * @return A {@link ReflectiveCancellable} that can be used to cancel the task.
     */
    ReflectiveCancellable schedule(Runnable task, Duration after, Duration every, Object context);
}
