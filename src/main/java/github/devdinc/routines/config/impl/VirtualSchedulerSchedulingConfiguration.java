package github.devdinc.routines.config.impl;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.impl.GenericCancellable;
import github.devdinc.routines.util.ReflectiveCancellable;
import github.devdinc.routines.util.Scheduler;

/**
 * A generic implementation of {@link SchedulingConfiguration}.
 */
public class VirtualSchedulerSchedulingConfiguration implements SchedulingConfiguration.ALL {

    @Override
    public Duration after() {
        return Duration.ZERO;
    }

    @Override
    public Duration every() {
        return Duration.ZERO;
    }

    @Override
    public Scheduler scheduler() {
        return (task, after, every, iContext) -> {
            GenericCancellable token = new GenericCancellable();

            Thread.startVirtualThread(() -> {
                if (after.isPositive())
                    LockSupport.parkNanos(after.toNanos());
                if (token.isCancelled())
                    return;
                if (!every.isPositive()) {
                    task.run();
                    return;
                }

                while (!token.isCancelled()) {
                    task.run();
                    LockSupport.parkNanos(every.toNanos());
                }
            });

            return new ReflectiveCancellable(token);
        };
    }
}
