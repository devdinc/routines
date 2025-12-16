package github.devdinc.routines.impl;

/**
 * A generic implementation of a cancellable task.
 * It provides a `cancel()` method to mark the task as cancelled and an `isCancelled()` method to check its status.
 */
public class GenericCancellable implements java.io.Serializable {
    private volatile boolean cancelled = false;

    /**
     * Marks the task as cancelled.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Checks if the task is cancelled.
     *
     * @return `true` if the task is cancelled, `false` otherwise.
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
