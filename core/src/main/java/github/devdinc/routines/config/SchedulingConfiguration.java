package github.devdinc.routines.config;

import java.time.Duration;

import github.devdinc.routines.util.Scheduler;

/**
 * This interface defines the scheduling parameters and scheduler for a routine.
 * It is decomposed into two inner interfaces:
 * A — timing configuration
 * B — scheduler provider
 */
public interface SchedulingConfiguration {

    interface ALL extends A, B {
    }

    /**
     * A: Defines delay and frequency configuration.
     */
    interface A {
        /**
         * The initial delay before the routine starts.
         *
         * @return the initial delay as a {@link Duration}
         */
        Duration after();

        /**
         * The interval between routine executions.
         *
         * @return the interval as a {@link Duration}
         */
        Duration every();
    }

    /**
     * B: Defines only the scheduler method.
     */
    interface B {

        /**
         * @return Context that scheduler interacts with, great for custom schedulers.
         */
        Object context();

        /**
         * The scheduler to be used for the routine.
         *
         * @return the {@link Scheduler} to be used
         */
        Scheduler scheduler();
    }
}
