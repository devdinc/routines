package github.devdinc.routines;

import java.time.Instant;

import github.devdinc.routines.config.RoutineConfiguration;

/**
 * The RoutineService class provides a simple way to create and manage routines.
 * It allows you to define a series of tasks that can be executed in a specific
 * order, with support for error handling, retries, and scheduling.
 * Routines are created using a fluent API, which makes it easy to define
 * complex workflows in a readable and maintainable way.
 */
public class RoutineService implements java.io.Serializable {
    private final RoutineConfiguration fc;

    public RoutineService(RoutineConfiguration fc) {
        this.fc = fc;
    }

    public RoutineService() {
        this(RoutineConfiguration.defaultRoutineConfig());
    }

    public void schedule(Task<?, ?> task) {
        task.scheduleExecution(Instant.now());
    }

    public void schedule(Task<?, ?> task, Instant initAt) {
        task.scheduleExecution(initAt);
    }

    /**
     * Starts a new fluent routine (input type = Void)
     *
     * @param initAt The time at which the routine should be initialized.
     * @return A new FluentRoutine instance.
     */
    public FluentRoutine<Void> fluent(Instant initAt) {
        return new TaskBuilder<>(fc, initAt);
    }
}
