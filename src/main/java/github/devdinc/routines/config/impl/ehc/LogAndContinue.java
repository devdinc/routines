package github.devdinc.routines.config.impl.ehc;

import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration;

/**
 * This class implements the LOG_AND_CONTINUE strategy for exception handling.
 * The exception is printed to the standard error stream and the routine
 * continues.
 */
public class LogAndContinue implements ExceptionHandlingConfiguration {
    @Override
    public ExceptionHandleRecord _onUncaughtException(Task<?, ?> task, Exception exception) {
        exception.printStackTrace();
        return new ExceptionHandleRecord(Strategy.CONTINUE, null);
    }
}