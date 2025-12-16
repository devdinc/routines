package github.devdinc.routines.config.impl.ehc;

import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration;

/**
 * This class implements the LOG_AND_STOP strategy for exception handling.
 * The exception is printed to the standard error stream and all routines are
 * stopped.
 */
public class LogAndStop implements ExceptionHandlingConfiguration, java.io.Serializable {
    @Override
    public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception exception) {
        exception.printStackTrace();
        return new ExceptionHandleRecord(Strategy.STOP_ALL, null);
    }
}