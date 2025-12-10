package github.devdinc.routines.config;

import github.devdinc.routines.config.impl.GenericExecutionConfiguration;
import github.devdinc.routines.config.impl.VirtualSchedulerSchedulingConfiguration;
import github.devdinc.routines.config.impl.ehc.LogAndContinue;

/**
 * A configuration for fluent routines.
 * This class provides a way to configure the exception handling, execution, and
 * scheduling of routines.
 */
public class RoutineConfiguration implements java.io.Serializable {
    private ExceptionHandlingConfiguration excc;

    /**
     * Gets the exception handling configuration.
     *
     * @return The exception handling configuration.
     */
    public ExceptionHandlingConfiguration getExcc() {
        return excc;
    }

    private ExecutionConfiguration exec;

    /**
     * Gets the execution configuration.
     *
     * @return The execution configuration.
     */
    public ExecutionConfiguration getExec() {
        return exec;
    }

    private SchedulingConfiguration.ALL sc;

    /**
     * Gets the scheduling configuration.
     *
     * @return The scheduling configuration.
     */
    public SchedulingConfiguration.ALL getSc() {
        return sc;
    }

    /**
     * Creates a new fluent configuration.
     *
     * @param excc The exception handling configuration.
     * @param exec The execution configuration.
     * @param sc   The scheduling configuration.
     */
    public RoutineConfiguration(ExceptionHandlingConfiguration excc, ExecutionConfiguration exec,
            SchedulingConfiguration.ALL sc) {
        this.excc = excc;
        this.exec = exec;
        this.sc = sc;
    }

    /**
     * Creates a new fluent configuration with default values.
     */
    public static RoutineConfiguration defaultRoutineConfig() {
        return new RoutineConfiguration(new LogAndContinue(), new GenericExecutionConfiguration(),
                new VirtualSchedulerSchedulingConfiguration());
    }
}
