package github.devdinc.routines.config;

import java.util.concurrent.Executor;

/**
 * This interface defines the configuration for executing routines.
 * It provides a way to specify the executor and the conditions for execution.
 */
public interface ExecutionConfiguration {
    /**
     * Gets the executor to be used for executing routines.
     *
     * @return The executor.
     */
    Executor executor();
}
