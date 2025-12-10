package github.devdinc.routines.config;

import github.devdinc.routines.Task;

/**
 * This interface defines the configuration for handling exceptions in routines.
 * It provides a method to handle uncaught exceptions and defines several
 * strategies for exception handling.
 */
public interface ExceptionHandlingConfiguration {
    /**
     * This method is called when an uncaught exception occurs in a task.
     *
     * @param task      The task that threw the exception.
     * @param exception The exception that was thrown.
     * @return An {@link ExceptionHandleRecord} that specifies the strategy to be
     *         used and any output.
     */
    ExceptionHandleRecord _onUncaughtException(Task<?, ?> task, Exception exception);

    /**
     * This enum defines the possible strategies for handling exceptions.
     */
    enum Strategy {
        /**
         * Continue with the execution of the routine.
         */
        CONTINUE,
        /**
         * Stop all routines.
         */
        STOP_ALL
    }

    /**
     * This record represents the result of handling an exception.
     * It contains the strategy to be used and any output.
     *
     * @param strategy The strategy to be used.
     * @param out      The output of the exception handling.
     */
    record ExceptionHandleRecord(Strategy strategy, Object out) {

    }
}
