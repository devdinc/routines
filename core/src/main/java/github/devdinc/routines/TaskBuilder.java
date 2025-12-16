package github.devdinc.routines;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import github.devdinc.routines.config.RoutineConfiguration;

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
    private volatile Task<Void,?> task;

    /** The transformation applied by this stage */
    private Function<? super I, ?> func;

    /** Configuration for this stage */
    private Duration after = Duration.ZERO;
    private Duration every = Duration.ZERO;
    private Executor executor;
    private Instant startTime;

    private Object ctx;

    /** --- Constructors --- */

    public TaskBuilder(RoutineConfiguration config, Instant startTime) {
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
    public FluentRoutine<I> carryExecutor() {
        if (previousBuilder != null)
            this.executor = previousBuilder.executor;
        return this;
    }

    protected Supplier<I> inputSupplier;

    /**
     * ------------------------------------------------------------
     * EXECUTION ENGINE
     * ------------------------------------------------------------
     */

    @SuppressWarnings("unchecked")
    private void runStage() {
        if (func == null)
            throw new IllegalStateException("apply(Function) was not provided");

        

        Task<Void, ?> t = new Task<Void, Object>(null, config) {

            @Override
            protected Object apply(Void i) {
                return func.apply(inputSupplier.get());
            }

            @Override
            public Duration after() {
                return after;
            }

            @Override
            public Duration every() {
                return every;
            }

            @Override
            public Object context() {
                return ctx;
            }
        };

        this.task = t;

        if (previousBuilder != null) {

            previousBuilder.task.onComplete(prevOut -> {
                I input = (I) prevOut;
                inputSupplier = () -> input;
                this.task.scheduleExecution(startTime);
            });

        } else {
            inputSupplier = () -> null;
            this.task.scheduleExecution(startTime);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public I join() {
        if (previousBuilder == null)
            throw new IllegalStateException("Cannot join root stage");
        return (I) previousBuilder.task.join();
    }

    @Override
    public FluentRoutine<I> context(Object ctx) {
        this.ctx = ctx;
        return this;
    }

}
