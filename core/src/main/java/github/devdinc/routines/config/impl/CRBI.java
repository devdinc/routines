package github.devdinc.routines.config.impl;

import java.time.Duration;

import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration;
import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.util.Scheduler;

/**
 * Configuration Resolution By Inheritance, providing default
 * configuration logic via classes.
 */
public abstract class CRBI {

    /**
     * Base configuration class, equivalent to CRBI.B.
     * Provides inherited implementations.
     */
    public abstract static class B
            implements /* ExceptionHandlingConfiguration, */
            SchedulingConfiguration.B, java.io.Serializable {

        protected RoutineConfiguration config;
        public B(RoutineConfiguration config) {
            this.config = config;
        }

        protected RoutineConfiguration getConfig() {
            return config;
        }

        protected void setConfig(RoutineConfiguration config) {
            this.config = config;
        }

        protected ExceptionHandlingConfiguration.ExceptionHandleRecord onUncaughtException(Task<?, ?> task,
                Exception exception) {
            return config.getExcc().onUncaughtException(task, exception);
        }

        @Override
        public Scheduler scheduler() {
            return config.getSc().scheduler();
        }

        @Override
        public Object context() {
            return config.getSc().context();
        }
    }

    /**
     * Full configuration class, equivalent to CRBI.ALL.
     */
    public abstract static class ALL extends B implements SchedulingConfiguration.A {

        public ALL(RoutineConfiguration config) {
            super(config);
        }

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
