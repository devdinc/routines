package github.devdinc.routines.config.impl;

import java.time.Duration;
import java.util.concurrent.Executor;

import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration;
import github.devdinc.routines.config.ExecutionConfiguration;
import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.util.Scheduler;

/**
 * Methodic Abstract Routine Configuration, providing default
 * configuration logic via abstract classes rather than interfaces.
 */
public abstract class MARC {

    /**
     * Base configuration class, equivalent to MARC.B.
     * Provides inherited default implementations.
     */
    public abstract static class B
            implements /* ExceptionHandlingConfiguration, */
            ExecutionConfiguration,
            SchedulingConfiguration.B {

        protected RoutineConfiguration config = RoutineConfiguration.defaultRoutineConfig();

        protected ExceptionHandlingConfiguration.ExceptionHandleRecord onUncaughtException(Task<?, ?> task,
                Exception exception) {
            return config.getExcc()._onUncaughtException(task, exception);
        }

        @Override
        public Executor executor() {
            return config.getExec().executor();
        }

        @Override
        public Scheduler scheduler() {
            return config.getSc().scheduler();
        }
    }

    /**
     * Full configuration class, equivalent to MARC.ALL.
     */
    public abstract static class ALL extends B implements SchedulingConfiguration.A {

        @Override
        public Duration after() {
            return config.getSc().after();
        }

        @Override
        public Duration every() {
            return config.getSc().every();
        }
    }
}
