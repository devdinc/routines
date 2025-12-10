package com.devdinc.routines;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Represents an intermediate, immutable builder stage for a {@link Routine}.
 * <p>
 * Allows configuration of asynchronous execution, scheduling, repeated tasks,
 * conditional execution, and delays. Each configuration method returns a new
 * {@link IncompleteRoutine} instance to maintain immutability and fluent
 * chaining.
 *
 * @param <V> the result type of the previous routine stage
 * @deprecated This class is deprecated and will be removed in a future release.
 *             Use the new implementation in {@link github.devdinc.routines.FluentRoutine} instead.
 */
@Deprecated
public class IncompleteRoutine<V> {

	private static final Executor DIRECT_EXECUTOR = Runnable::run;

	private final CompletableFuture<V> previousFuture;
	private final Temporal temporalAnchor;
	private final Executor asyncExecutor;
	private final boolean isAsync;
	private final Predicate<? super V> condition;
	private final Duration repeatInterval;
	private final Duration initialDelay;

	// ---- Constructors ----

	/**
	 * Creates a new IncompleteRoutine anchored to a specific temporal time
	 * and optionally chained after a previous CompletableFuture.
	 *
	 * @param temporalAnchor the time to start execution (may be null)
	 * @param previousFuture the previous stage's future (may be null)
	 */
	protected IncompleteRoutine(Temporal temporalAnchor, CompletableFuture<V> previousFuture) {
		this(temporalAnchor, previousFuture, null, false, null, Duration.ZERO, Duration.ZERO);
	}

	/**
	 * Full constructor used internally to create modified copies of this routine
	 * stage.
	 *
	 * @param temporalAnchor the time anchor
	 * @param previousFuture the previous stage future
	 * @param asyncExecutor  the executor for async execution
	 * @param isAsync        whether this stage runs asynchronously
	 * @param condition      optional condition predicate for execution
	 * @param repeatInterval repeat interval for periodic execution
	 * @param initialDelay   initial delay before first execution
	 */
	private IncompleteRoutine(
			Temporal temporalAnchor,
			CompletableFuture<V> previousFuture,
			Executor asyncExecutor,
			boolean isAsync,
			Predicate<? super V> condition,
			Duration repeatInterval,
			Duration initialDelay) {
		this.temporalAnchor = temporalAnchor;
		this.previousFuture = previousFuture;
		this.asyncExecutor = asyncExecutor;
		this.isAsync = isAsync;
		this.condition = condition;
		this.repeatInterval = repeatInterval;
		this.initialDelay = initialDelay;
	}

	// ---- Configuration Methods ----

	/**
	 * Configures this routine stage to execute asynchronously using the default
	 * executor.
	 *
	 * @return a new {@link IncompleteRoutine} instance configured for async
	 *         execution
	 */
	public IncompleteRoutine<V> async() {
		return new IncompleteRoutine<>(
				temporalAnchor, previousFuture, defaultAsyncExecutor(),
				true, condition, repeatInterval, initialDelay);
	}

	/**
	 * Configures this routine stage to execute asynchronously using a custom
	 * executor.
	 *
	 * @param executor the executor to run tasks on
	 * @return a new {@link IncompleteRoutine} instance configured for async
	 *         execution
	 */
	public IncompleteRoutine<V> async(Executor executor) {
		return new IncompleteRoutine<>(
				temporalAnchor, previousFuture, executor,
				true, condition, repeatInterval, initialDelay);
	}

	/**
	 * Adds a conditional predicate to determine whether the task executes.
	 *
	 * @param condition the predicate that must return true for execution
	 * @return a new {@link IncompleteRoutine} instance with the condition applied
	 */
	public IncompleteRoutine<V> conditional(Predicate<? super V> condition) {
		return new IncompleteRoutine<>(
				temporalAnchor, previousFuture, asyncExecutor,
				isAsync, condition, repeatInterval, initialDelay);
	}

	/**
	 * Sets a delay before executing the task.
	 *
	 * @param duration the delay duration (negative or null is treated as zero)
	 * @return a new {@link IncompleteRoutine} with the specified initial delay
	 */
	public IncompleteRoutine<V> after(Duration duration) {
		return new IncompleteRoutine<>(
				temporalAnchor, previousFuture, asyncExecutor,
				isAsync, condition, repeatInterval, nonNull(duration));
	}

	/**
	 * Sets a repeating interval for periodic execution of the task.
	 *
	 * @param duration the interval duration (negative or null is treated as zero)
	 * @return a new {@link IncompleteRoutine} configured for periodic execution
	 */
	public IncompleteRoutine<V> every(Duration duration) {
		return new IncompleteRoutine<>(
				temporalAnchor, previousFuture, asyncExecutor,
				isAsync, condition, nonNull(duration), initialDelay);
	}

	// ---- Terminal Operations ----

	/**
	 * Executes a task consuming the result of the previous stage.
	 *
	 * @param task the consumer to run
	 * @return a {@link Routine} representing the completed stage
	 */
	public Routine<Void> accept(Consumer<? super V> task) {
		return apply(v -> {
			task.accept(v);
			return null;
		});
	}

	/**
	 * Executes a Runnable task, ignoring any previous result.
	 *
	 * @param task the task to run
	 * @return a {@link Routine} representing the completed stage
	 */
	public Routine<Void> run(Runnable task) {
		return apply(v -> {
			task.run();
			return null;
		});
	}

	/**
	 * Supplies a value or computation to produce the next stage result.
	 *
	 * @param <R>  the result type of the task
	 * @param task the supplier function
	 * @return a {@link Routine} representing the completed stage
	 */
	public <R> Routine<R> supply(Supplier<R> task) {
		return apply(v -> task.get());
	}

	/**
	 * Core operation applying a function to the previous result.
	 *
	 * @param <R>  the result type of the function
	 * @param task the function to apply
	 * @return a {@link Routine} representing the next stage
	 */
	public <R> Routine<R> apply(Function<? super V, ? extends R> task) {
		final CompletableFuture<R> nextFuture = new CompletableFuture<>();
		final Executor executor = isAsync
				? (asyncExecutor != null ? asyncExecutor : defaultAsyncExecutor())
				: DIRECT_EXECUTOR;

		final Duration delay = computeInitialDelay();

		if (previousFuture != null) {
			previousFuture.whenComplete((prevResult, ex) -> {
				if (ex != null) {
					nextFuture.completeExceptionally(ex);
				} else {
					scheduleLoop(task, prevResult, executor, nextFuture, delay);
				}
			});
		} else {
			scheduleLoop(task, null, executor, nextFuture, delay);
		}

		return new Routine<>(nextFuture);
	}

	// ---- Internal Helpers ----

	private <R> void scheduleLoop(
			Function<? super V, ? extends R> task,
			V prevResult,
			Executor executor,
			CompletableFuture<R> nextFuture,
			Duration delay) {
		final Executor scheduler = defaultSchedulerExecutor();
		scheduler.execute(() -> {
			safeSleep(delay);

			while (!nextFuture.isDone()) {
				final AtomicBoolean completed = new AtomicBoolean(false);
				final CountDownLatch latch = new CountDownLatch(1);

				runOnce(task, prevResult, executor, nextFuture, completed, latch);

				try {
					latch.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					nextFuture.completeExceptionally(e);
					logIfSilent(e);
					break;
				}

				if (completed.get() || repeatInterval.isZero())
					break;
				safeSleep(repeatInterval);
			}
		});
	}

	private <R> void runOnce(
			Function<? super V, ? extends R> task,
			V input,
			Executor executor,
			CompletableFuture<R> nextFuture,
			AtomicBoolean completed,
			CountDownLatch latch) {
		executor.execute(() -> {
			try {
				if (condition == null || condition.test(input)) {
					final R result = task.apply(input);
					if (repeatInterval.isZero() || result != null) {
						completed.set(nextFuture.complete(result));
					}
				} else if (repeatInterval.isZero()) {
					throw new RuntimeException("Condition not met");
				}
			} catch (Throwable t) {
				nextFuture.completeExceptionally(t);
				completed.set(true);
				logIfSilent(t);
			} finally {
				latch.countDown();
			}
		});
	}

	private static void logIfSilent(Throwable t) {
		System.err.printf("[%s] Routine error, Uncaught silent exception: %s%n", Thread.currentThread().getName(), t);
		t.printStackTrace(System.err);
	}

	private Duration computeInitialDelay() {
		Duration base = Duration.ZERO;
		if (temporalAnchor != null) {
			base = Duration.between(LocalDateTime.now(), temporalAnchor);
		}
		final Duration total = base.plus(initialDelay);
		return total.isNegative() ? Duration.ZERO : total;
	}

	private static void safeSleep(Duration duration) {
		if (duration == null || duration.isZero() || duration.isNegative())
			return;
		LockSupport.parkNanos(duration.toNanos());
	}

	private static Duration nonNull(Duration d) {
		return (d == null || d.isNegative()) ? Duration.ZERO : d;
	}

	// ---- Executors ----

	/**
	 * Returns the default executor for asynchronous execution using virtual
	 * threads.
	 */
	protected Executor defaultAsyncExecutor() {
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
	}

	/** Returns the default executor for scheduling tasks. */
	protected Executor defaultSchedulerExecutor() {
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
	}

	// ---- Debug ----

	@Override
	public String toString() {
		return String.format(
				"IncompleteRoutine{previousFuture=%s, temporalAnchor=%s, asyncExecutor=%s, isAsync=%s, " +
						"condition=%s, repeatInterval=%s, initialDelay=%s}",
				previousFuture, temporalAnchor, asyncExecutor, isAsync,
				condition, repeatInterval, initialDelay);
	}
}
