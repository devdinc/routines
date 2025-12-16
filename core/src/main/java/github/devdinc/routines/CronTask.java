package github.devdinc.routines;

import java.time.Instant;
import java.util.List;

import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.config.impl.CRBI;
import github.devdinc.routines.cron.Cron;

@SuppressWarnings("unused")
public abstract class CronTask extends CRBI.B
        implements java.io.Serializable {

    private final Cron cron;
    private final RoutineConfiguration defaults;

    protected CronTask(Cron cron, RoutineConfiguration defaults) {
        super(defaults);
        this.cron = cron;
        this.defaults = defaults;
        scheduleNext();
    }

    public CronTask(Cron cron) {
        this(cron, RoutineConfiguration.defaultRoutineConfig());
    }

    private void scheduleNext() {
        List<Instant> next = cron.next(1, Instant.now(), null);
        if (next.isEmpty()) {
            return; // no more executions
        }

        Instant instant = next.get(0);

        Task<Void, Void> task = new Task<>(null, defaults) {
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
