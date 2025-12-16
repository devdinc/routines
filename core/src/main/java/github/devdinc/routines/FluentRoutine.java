package github.devdinc.routines;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The FluentRoutine interface provides a fluent API for creating and managing
 * routines.
 * It allows you to define a series of tasks that can be executed in a specific
 * order, with support for error handling, retries, and scheduling.
 *
 * @param <I> The input type of the routine.
 */

public interface FluentRoutine<I> {

    FluentRoutine<I> carryExecutor();

    FluentRoutine<I> after(Duration duration);

    FluentRoutine<I> every(Duration duration);

    <R> FluentRoutine<R> apply(Function<? super I, ? extends R> task);

    default <R> FluentRoutine<R> supply(Supplier<R> supplier) {
        return apply(i -> supplier.get());
    }

    default FluentRoutine<Void> accept(Consumer<I> consumer) {
        return apply(i -> {
            consumer.accept(i);
            return null;
        });
    }

    default FluentRoutine<Void> run(Runnable runnable) {
        return apply(i -> {
            runnable.run();
            return null;
        });
    }

    /** Marker method – no type transformation, improves readability. */
    default FluentRoutine<I> then() {
        return this;
    }

    I join();

    /** Used for communicating with the scheduler. Eg. Folia */
    FluentRoutine<I> context(Object ctx);
}
