package github.devdinc.routines;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import github.devdinc.routines.config.ExceptionHandlingConfiguration.ExceptionHandleRecord;
import github.devdinc.routines.config.impl.MARC;
import github.devdinc.routines.util.ReflectionUtils;
import github.devdinc.routines.util.ReflectiveCancellable;
import github.devdinc.routines.util.Result;
import github.devdinc.routines.util.Scheduler;
import github.devdinc.routines.util.SerializableConsumer;

/**
 * An abstract base class for defining tasks that can be executed
 * asynchronously, scheduled to run at specific times or intervals, and chained
 * together to create complex workflows.
 *
 * @param <I> The type of the input to the task.
 * @param <O> The type of the output from the task.
 */
public abstract class Task<I, O> extends MARC.ALL implements java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    protected final List<SerializableConsumer<O>> onCompleteHandlers = new CopyOnWriteArrayList<>();

    /** The input to the task. This may be null. */
    protected final I input;

    /** A flag indicating whether the task returns a {@link Result} object. */
    protected final boolean isResultTask = ReflectionUtils.isResultTask(this.getClass());

    /**
     * A flag indicating whether the task returns a {@link Result} object where the
     * error type is an {@link Exception}.
     */
    protected final boolean isResultTaskWithExceptionError = isResultTask
            && ReflectionUtils.resultSecondTypeIsException(this.getClass());

    /** The last result of the task's execution. This may be null. */
    protected final AtomicReference<O> lastResult = new AtomicReference<>();

    /** A flag indicating whether the task has completed execution. */
    protected final AtomicBoolean isComplete = new AtomicBoolean(false);

    /** A lock object used for synchronization in the {@link #join()} methods. */
    protected final transient Object joinLock = new Object();

    /** A reference to the cancellation token for the task's scheduler. */
    protected final AtomicReference<ReflectiveCancellable> tokenRef = new AtomicReference<>();

    /**
     * A flag indicating whether the task is currently scheduled or running.
     * It is true during the task's active lifecycle and false when it's idle.
     */
    private final AtomicBoolean schedulingActive = new AtomicBoolean(false);

    /**
     * Default constructor. Initializes the task with null input and does not
     * register it for immediate execution.
     */
    protected Task() {
        this(null, false);
    }

    /**
     * Constructor to create a task with the given input.
     *
     * @param input               The input to the task.
     * @param registerImmediately If true, the task will be scheduled for
     *                            immediate execution.
     */
    protected Task(I input, boolean registerImmediately) {
        this.input = input;
        if (registerImmediately)
            register();
    }

    /**
     * Registers the task for execution. This is a convenience method that
     * calls {@link #scheduleExecution()}.
     */
    protected void register() {
        scheduleExecution();
    }

    // ------------------------------------------------------------
    // ABSTRACT USER LOGIC
    // ------------------------------------------------------------

    /**
     * @return Context that scheduler interacts with, great for custom schedulers.
     */
    protected Object context() { // Might pass it down to executor too, so not inside Schedu...Conf...tion.B
        return null;
    }

    /**
     * The core operation performed by this task. This method must be implemented
     * by subclasses.
     *
     * @param input The input to the task.
     * @return The output of the task.
     */
    protected abstract O apply(I input);

    /**
     * Defines the next task in a chain. This method can be overridden to create
     * a sequence of tasks.
     *
     * @param result The result of the current task's execution.
     * @return The next task to be executed, or null if there is no next task.
     */
    protected Task<O, ?> next(O result) {
        return null;
    }

    // ------------------------------------------------------------
    // COMPLETION AND STATE
    // ------------------------------------------------------------

    /**
     * Registers a callback to be executed when the task completes.
     * If the task is already complete at registration time, the callback
     * is invoked immediately. This will execute in the same thread.
     *
     * @param consumer A consumer receiving the final output of the task.
     */
    public void onComplete(SerializableConsumer<O> consumer) {
        // If already completed, invoke immediately
        if (isComplete.get()) {
            O out = lastResult.get();
            try {
                consumer.accept(out);
            } catch (Exception ignored) {
                // Swallow exceptions from user callback
            }
        }

        onCompleteHandlers.add(consumer);
    }

    /**
     * Checks if the task is complete.
     *
     * @return True if the task is complete, false otherwise.
     */
    public boolean isComplete() {
        return isComplete.get();
    }

    /**
     * Determines if this task will no longer repeat. A task will not repeat if
     * its execution interval is not a positive duration or if it has been
     * cancelled.
     *
     * @return True if the task will not repeat, false otherwise.
     */
    private boolean willNotRepeat() {
        Duration ev = every();
        boolean everyPos = (ev != null && ev.compareTo(Duration.ZERO) > 0);

        ReflectiveCancellable tok = tokenRef.get();
        boolean cancelled = tok != null && tok.isCancelled();

        return !everyPos || cancelled;
    }

    // ------------------------------------------------------------
    // JOIN
    // ------------------------------------------------------------

    /**
     * Waits for the task to complete and then returns its result. This is a
     * blocking operation.
     *
     * @return The result of the task's execution.
     */
    public O join() {
        if (isComplete.get())
            return lastResult.get();

        synchronized (joinLock) {
            while (!isComplete.get()) {
                try {
                    joinLock.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return lastResult.get();
    }

    /**
     * Waits for the task to complete for a specified duration and then returns
     * its result.
     *
     * @param timeout The maximum time to wait for the task to complete.
     * @return The result of the task's execution.
     * @throws IllegalStateException If the task times out before completion.
     */
    public O join(Duration timeout) {
        if (isComplete.get())
            return lastResult.get();

        long limit = timeout == null ? 0 : timeout.toMillis();
        if (limit <= 0) {
            if (!isComplete.get())
                throw new IllegalStateException("Task timed out");
            return lastResult.get();
        }

        final long end = System.currentTimeMillis() + limit;

        synchronized (joinLock) {
            while (!isComplete.get()) {
                long now = System.currentTimeMillis();
                long remaining = end - now;
                if (remaining <= 0)
                    break;

                try {
                    joinLock.wait(remaining);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!isComplete.get())
            throw new IllegalStateException("Task timed out");

        return lastResult.get();
    }

    /**
     * Cancels the task's execution. This will stop any further repetitions and
     * release any waiting threads in {@link #join()}.
     */
    public void cancel() {
        ReflectiveCancellable tok = tokenRef.get();
        if (tok != null)
            tok.cancel();

        // Release scheduling guard
        schedulingActive.set(false);

        // Mark complete + notify joiners
        markComplete(lastResult.get());
    }

    /**
     * Marks the task as complete and notifies any waiting threads.
     *
     * @param out The final output of the task.
     */
    private void markComplete(O out) {
        lastResult.set(out);
        isComplete.set(true);

        for (SerializableConsumer<O> cb : onCompleteHandlers) {
            try {
                cb.accept(out);
            } catch (Exception ignored) {
            }
        }

        synchronized (joinLock) {
            joinLock.notifyAll();
        }
    }

    // ------------------------------------------------------------
    // EXECUTION INTERNALS
    // ------------------------------------------------------------

    /**
     * A wrapper for {@link #runInternal()} that ensures the scheduling guard is
     * reset properly after execution.
     */
    private void runInternalProtected() {
        try {
            runInternal();
        } finally {
            if (willNotRepeat())
                schedulingActive.set(false);
        }
    }

    /**
     * The internal execution logic of the task. This method handles conditions,
     * exceptions, and chaining to the next task.
     */
    @SuppressWarnings("unchecked")
    private void runInternal() {
        O out = null;

        try {
            out = apply(input);
        } catch (Exception ex) {

            ExceptionHandleRecord record = onUncaughtException(this, ex);
            out = (O) record.out();

            switch (record.strategy()) {
                case CONTINUE:
                    break;

                case STOP_ALL: {
                    ReflectiveCancellable tok = tokenRef.get();
                    if (tok != null)
                        tok.cancel();
                    markComplete(out);
                    return;
                }
            }
        }

        lastResult.set(out);

        finishRepeatingTaskWhenNonErrorOrNull(out);

        if (willNotRepeat())
            markComplete(out);

        Task<O, ?> nxt = next(out);
        if (willNotRepeat() && nxt != null)
            nxt.scheduleExecution();
    }

    /**
     * Logic to finish a repeating task. If the output is a {@link Result} and it
     * is successful, or if the output is not null for a non-Result task, the
     * repetition is cancelled.
     *
     * @param out The output of the task.
     */
    private void finishRepeatingTaskWhenNonErrorOrNull(O out) {
        if (out == null)
            return;

        ReflectiveCancellable tok = tokenRef.get();
        if (tok == null)
            return;

        if (isResultTask) {
            Result<?, ?> r = (Result<?, ?>) out;
            if (r.isOk())
                tok.cancel();
        } else {
            tok.cancel();
        }
    }

    // ------------------------------------------------------------
    // SCHEDULING
    // ------------------------------------------------------------

    /**
     * Schedules the task for execution.
     *
     * @return A {@link ReflectiveCancellable} token that can be used to cancel
     *         the scheduled execution.
     */
    protected final ReflectiveCancellable scheduleExecution() {
        return scheduleExecution(Instant.now());
    }

    /**
     * Schedules the task for execution at a specific time.
     *
     * @param initAt The time at which the task should be executed.
     * @return A {@link ReflectiveCancellable} token that can be used to cancel
     *         the scheduled execution.
     * @throws IllegalStateException If no scheduler is available.
     */
    protected final ReflectiveCancellable scheduleExecution(Instant initAt) {
        if (initAt == null)
            initAt = Instant.now();

        // -----------------------------------------
        // MULTI-SCHEDULING GUARD
        // Only one scheduling can occur at a time.
        // A completed task *may* be scheduled again.
        // -----------------------------------------
        if (!schedulingActive.compareAndSet(false, true)) {
            // Already scheduled — return existing token
            return tokenRef.get();
        }

        Duration after = after() == null ? Duration.ZERO : after();
        Duration offset = Duration.between(Instant.now(), initAt);
        Duration delay = after.plus(offset);

        if (delay.isNegative())
            delay = Duration.ZERO;

        Duration every = every() == null ? Duration.ZERO : every();
        Executor exec = executor();

        Scheduler sched = scheduler();
        if (sched == null)
            throw new IllegalStateException("No scheduler available");

        ReflectiveCancellable tok = sched.schedule(() -> {
            if (exec != null)
                exec.execute(this::runInternalProtected);
            else
                runInternalProtected();
        }, delay, every, context());

        tokenRef.set(tok);
        return tok;
    }
}
