package github.devdinc.routines.config.impl.ehc;

import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration;
import github.devdinc.routines.util.ReflectionUtils;
import github.devdinc.routines.util.Result;

/**
 * This class implements the Carry strategy for exception handling.
 * The exception is propagated to the next Task. Task's result type must be a
 * {@link Result}<1, 2> where 2 is an {@link Exception}.
 */
public class Carry implements ExceptionHandlingConfiguration, java.io.Serializable {
    @Override
    public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception exception) {
        final boolean isResultTaskWithExceptionError = ReflectionUtils.resultSecondTypeIsException(task.getClass());
        if (!isResultTaskWithExceptionError)
            throw new IllegalStateException("Not a result task with Exception Error");
        return new ExceptionHandleRecord(Strategy.CONTINUE, Result.err(exception));
    }
}