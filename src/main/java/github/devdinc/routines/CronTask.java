package github.devdinc.routines;

import java.time.Instant;
import java.util.List;

import github.devdinc.routines.config.impl.MARC;
import github.devdinc.routines.cron.Cron;

public abstract class CronTask extends MARC.B
        implements java.io.Serializable {

    private final Cron cron;

    protected CronTask(Cron cron) {
        this.cron = cron;
        scheduleNext();
    }

    private void scheduleNext() {
        List<Instant> next = cron.next(1, Instant.now(), null);
        if (next.isEmpty()) {
            return; // no more executions
        }

        Instant instant = next.get(0);

        Task<Void, Void> task = new Task<>(null, false) {
            @Override
            protected Void apply(Void i) {
                run();
                scheduleNext(); // schedule the next run dynamically
                return null;
            }
        };

        task.scheduleExecution(instant);
    }

    /**
     * This method must be implemented by subclasses to define the actual work of
     * the task.
     *
     * @return The output of the task.
     */
    protected abstract void run();

}
