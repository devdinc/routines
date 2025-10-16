package com.devdinc.routines;

import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Represents a routine, which is a chain of tasks that can be scheduled
 * to run at specific times, repeated intervals, or based on other routines.
 *
 * <p>Routines can be combined, executed concurrently, or run conditionally.
 *
 * @param <V> the result type of this routine stage
 */
public class Routine<V> {

	/** The internal CompletableFuture representing this routine's computation. */
	protected final CompletableFuture<V> completableFuture;

	/** The Paper plugin context associated with this routine. */
	protected final Plugin plugin;

	/**
	 * Constructs a Routine wrapping a given CompletableFuture and plugin context.
	 *
	 * @param plugin             the plugin context (for scheduling)
	 * @param completableFuture  the underlying CompletableFuture
	 */
	protected Routine(Plugin plugin, CompletableFuture<V> completableFuture) {
		this.plugin = plugin;
		this.completableFuture = completableFuture;
	}

	/**
	 * Creates a new routine that will start execution at the specified temporal date/time.
	 *
	 * @param plugin the plugin context
	 * @param date   the temporal anchor at which the routine starts
	 * @return an {@link IncompleteRoutine} for configuring the task(s)
	 */
	public static IncompleteRoutine<Void> at(Plugin plugin, Temporal date) {
		return new IncompleteRoutine<>(plugin, date, null);
	}

	/**
	 * Creates a new routine that starts immediately.
	 *
	 * @param plugin the plugin context
	 * @return an {@link IncompleteRoutine} ready for task configuration
	 */
	public static IncompleteRoutine<Void> now(Plugin plugin) {
		return at(plugin, LocalDateTime.now());
	}

	/**
	 * Creates a new routine triggered by a cron expression.
	 *
	 * @param plugin         the plugin context
	 * @param cronExpression the cron expression in standard Unix format
	 * @return an {@link IncompleteRoutine} for scheduling the cron job
	 */
	public static IncompleteRoutine<Void> cron(Plugin plugin, String cronExpression) {
		return CronRoutineDelegate.unix(plugin, cronExpression);
	}

	/**
	 * Combines two routines into a single routine whose result is computed
	 * by applying a combiner function to the results of the two routines.
	 *
	 * @param <A>      result type of the first routine
	 * @param <B>      result type of the second routine
	 * @param <R>      result type of the combined routine
	 * @param r1       the first routine
	 * @param r2       the second routine
	 * @param combiner the function combining the results of r1 and r2
	 * @return a new routine producing the combined result
	 */
	public static <A, B, R> Routine<R> combine(Routine<A> r1, Routine<B> r2, BiFunction<A, B, R> combiner) {
		CompletableFuture<R> combined = r1.completableFuture.thenCombine(r2.completableFuture, combiner);
		// prefer r1's plugin context, or fallback to r2’s
		Plugin plugin = (r1.plugin != null) ? r1.plugin : r2.plugin;
		return new Routine<>(plugin, combined);
	}

	/**
	 * Creates a routine that completes when all provided routines complete.
	 *
	 * @param routines the routines to wait for
	 * @return a new routine that completes when all input routines complete
	 */
	public static Routine<Void> allOf(Routine<?>... routines) {
		CompletableFuture<Void> all = CompletableFuture.allOf(
				Arrays.stream(routines).map(r -> r.completableFuture).toArray(CompletableFuture[]::new)
		);
		Plugin plugin = (routines.length > 0) ? routines[0].plugin : null;
		return new Routine<>(plugin, all);
	}

	/**
	 * Creates a routine that completes when any one of the provided routines completes.
	 *
	 * @param routines the routines to wait for
	 * @return a new routine that completes when any of the input routines completes
	 */
	public static Routine<Object> anyOf(Routine<?>... routines) {
		CompletableFuture<Object> any = CompletableFuture.anyOf(
				Arrays.stream(routines).map(r -> r.completableFuture).toArray(CompletableFuture[]::new)
		);
		Plugin plugin = (routines.length > 0) ? routines[0].plugin : null;
		return new Routine<>(plugin, any);
	}

	/**
	 * Starts a new stage that runs after this routine completes.
	 *
	 * <p>Allows chaining of tasks in a fluent style.
	 *
	 * @return an {@link IncompleteRoutine} representing the next stage
	 */
	public IncompleteRoutine<V> then() {
		return new IncompleteRoutine<>(plugin, null, completableFuture);
	}

	/**
	 * Waits for this routine to complete and returns its result.
	 * This method blocks the calling thread until completion.
	 *
	 * @return the result of the routine
	 * @throws java.util.concurrent.CompletionException if the routine completes exceptionally
	 */
	public V join() {
		return completableFuture.join();
	}
}
