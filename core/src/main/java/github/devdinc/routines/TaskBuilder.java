package github.devdinc.routines;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import github.devdinc.routines.config.ExceptionHandlingConfiguration;
import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.util.Scheduler;

/**
 * TaskBuilder is the concrete implementation of the new FluentRoutine<I>.
 * Each TaskBuilder<I> represents a stage that consumes type I.
 *
 * Next stage is created via apply(), producing TaskBuilder<R>.
 */
public final class TaskBuilder<I> implements FluentRoutine<I> {

    private final RoutineConfiguration config;

    /** Previous stage, null if this is first */
    private final TaskBuilder<?> previousBuilder;

    /** Executed task for this stage */
    private Task<I, ?> task;

    /** The transformation applied by this stage */
    private Function<? super I, ?> func;

    /** Configuration for this stage */
    private Duration after = Duration.ZERO;
    private Duration every = Duration.ZERO;
    private Executor executor;
    private final List<BooleanSupplier> conditions = new ArrayList<>();
    private Temporal startTime;

    private Object ctx;

    /** --- Constructors --- */

    public TaskBuilder(RoutineConfiguration config, Temporal startTime) {
        this.config = config;
        this.previousBuilder = null;
        this.startTime = startTime;
    }

    private TaskBuilder(RoutineConfiguration config, TaskBuilder<?> previous) {
        this.config = config;
        this.previousBuilder = previous;
    }

    /**
     * ------------------------------------------------------------
     * FluentRoutine<I> IMPLEMENTATION
     * ------------------------------------------------------------
     */

    @Override
    public <R> FluentRoutine<R> apply(Function<? super I, ? extends R> fn) {
        this.func = fn;
        return buildNextStage(fn);
    }

    private <R> TaskBuilder<R> buildNextStage(Function<? super I, ? extends R> fn) {
        runStage();
        return new TaskBuilder<>(config, this);
    }

    @Override
    public FluentRoutine<I> after(Duration d) {
        this.after = d;
        return this;
    }

    @Override
    public FluentRoutine<I> every(Duration d) {
        this.every = d;
        return this;
    }

    @Override
    public FluentRoutine<I> async(Executor ex) {
        this.executor = ex;
        return this;
    }

    @Override
    public FluentRoutine<I> carryExecutor() {
        if (previousBuilder != null)
            this.executor = previousBuilder.executor;
        return this;
    }

    /** Additional API */
    public TaskBuilder<I> conditional(BooleanSupplier... c) {
        conditions.addAll(List.of(c));
        return this;
    }

    /**
     * ------------------------------------------------------------
     * EXECUTION ENGINE
     * ------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    private void runStage() {
        if (func == null)
            throw new IllegalStateException("apply(Function) was not provided");

        if (previousBuilder != null && previousBuilder.task != null) {

            previousBuilder.task.onComplete(prevOut -> {
                I input = (I) prevOut;
                startThisStage(input);
            });

        } else {
            startThisStage(null);
        }
    }

    private void startThisStage(I input) {
        final Duration startDelay = (startTime != null)
                ? Duration.between(Instant.now(), startTime).plus(after)
                : after;

        Task<I, ?> t = new Task<I, Object>(input, false) {

            @Override
            protected Object apply(I i) {
                return func.apply(i);
            }

            @Override
            public Duration after() {
                return startDelay;
            }

            @Override
            public Duration every() {
                return every;
            }

            @Override
            public ExceptionHandlingConfiguration.ExceptionHandleRecord onUncaughtException(
                    Task<?, ?> task, Exception ex) {
                return config.getExcc()._onUncaughtException(task, ex);
            }

            @Override
            public Scheduler scheduler() {
                return config.getSc().scheduler();
            }

            @Override
            public Object context() {
                return ctx;
            }
        };

        t.scheduleExecution(Instant.now());
        this.task = t;
    }

    @SuppressWarnings("unchecked")
    @Override
    public I join() {
        return (I) previousBuilder.task.join();
    }

    @Override
    public FluentRoutine<I> context(Object ctx) {
        this.ctx = ctx;
        return this;
    }

}
