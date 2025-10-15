package com.devdinc.routines;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RoutineTest {

	/**
	 * Runs a simple synchronous runnable via Routine.now().run(...)
	 * Expectation: completes and does the side-effect.
	 */
	@Test
	public void testRunImmediateSync() {
		AtomicInteger flag = new AtomicInteger(0);

		// limit the whole test to avoid indefinite hangs
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			Routine.now()
					.run(() -> flag.set(7))
					.join();
		});

		assertEquals(7, flag.get());
	}

	/**
	 * Tests that supply returns a value and join returns that value.
	 */
	@Test
	public void testSupplyReturnsValue() {
		String result = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.supply(() -> "hello")
						.join()
		);

		assertEquals("hello", result);
	}

	/**
	 * Tests that exceptions thrown inside supply are propagated as CompletionException from join().
	 */
	@Test
	public void testApplyThrowsException() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			CompletableFuture<Void> testFuture = CompletableFuture.runAsync(() -> {
				Routine<Void> r = Routine.now()
						.apply((v) -> {
							throw new IllegalStateException("boom");
						});

				try {
					r.join();
					fail("Expected an exception from join()");
				} catch (CompletionException ce) {
					// expected; verify cause type and message
					Throwable cause = ce.getCause();
					assertNotNull(cause);
					assertInstanceOf(IllegalStateException.class, cause);
					assertEquals("boom", cause.getMessage());
				}
			});

			// ensure the test doesn't hang: wait for internal check to finish
			testFuture.get(1500, TimeUnit.MILLISECONDS);
		});
	}

	/**
	 * Tests chaining: supply a value, then() and apply a function that receives the previous result.
	 */
	@Test
	public void testThenChainsPreviousResult() {
		String joined = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.supply(() -> "first")
						.then()
						.apply(prev -> prev + "-second")
						.join()
		);

		assertEquals("first-second", joined);
	}

	/**
	 * Tests allOf and anyOf utility methods.
	 * - allOf should complete after both routines complete.
	 * - anyOf should complete with one of the results.
	 */
	@Test
	public void testAllOfAnyOf() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			Routine<String> r1 = Routine.now()
					.supply(() -> {
						// small busy-wait to simulate work (very short)
						Thread.yield();
						return "A";
					});

			Routine<String> r2 = Routine.now()
					.supply(() -> {
						Thread.yield();
						return "B";
					});

			// allOf -> returns Void; should complete successfully
			Routine<Void> all = Routine.allOf(r1, r2);
			all.join(); // should not throw

			// anyOf -> returns one of the supplied results
			Routine<Object> any = Routine.anyOf(r1, r2);
			Object anyResult = any.join();
			assertTrue("A".equals(anyResult) || "B".equals(anyResult));
		});
	}

	/**
	 * Tests the every(...) repetition behaviour:
	 * supply returns null twice then a non-null string; the routine should keep retrying
	 * until a non-null value is returned (and then complete).
	 */
	@Test
	public void testEveryRetriesUntilNonNull() {
		AtomicInteger counter = new AtomicInteger(0);

		String result = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
				Routine.now()
						// minimal after delay
						.every(Duration.ofNanos(1)) // very small pause between retries
						.supply(() -> {
							int v = counter.getAndIncrement();
							if (v < 2) {
								// first two attempts return null -> should retry
								return null;
							} else {
								return "done-" + v;
							}
						})
						.join()
		);

		// ensure it eventually returned a non-null result and that the counter increased
		assertNotNull(result);
		assertTrue(result.startsWith("done-"));
		assertTrue(counter.get() >= 3, () -> "expected at least 3 attempts, got: " + counter.get());
	}

	/**
	 * Tests that chaining with a previous stage's result and condition works.
	 * We create a supply stage that returns "ok", then a .then() stage that only completes
	 * (via conditional) if the previous value matches.
	 */
	@Test
	public void testConditionalWithPrevResult() {
		String finalResult = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.supply(() -> "magic")
						.then()
						.conditional("magic"::equals)
						.apply(prev -> prev + "-accepted")
						.join()
		);

		assertEquals("magic-accepted", finalResult);
	}

	/**
	 * Tests that cron() passes correct incomplete routine.
	 */
	@Test
	public void testCron() {
		IncompleteRoutine<Void> cron = Routine.cron("* * * * *");
		assertTrue(cron.toString().contains("repeatInterval=" + Duration.ofMinutes(1)));
	}

	// async after accept test
	@Test
	public void testAsyncAfterAccept() {
		AtomicBoolean completed = new AtomicBoolean(false);
		Routine.now()
				.async()
				.after(Duration.ofNanos(1))
				.accept(v -> completed.set(true))
				.join();
		assertTrue(completed.get());
	}

	@Test
	public void testAfterDurationCompletion() {
		long start = System.nanoTime();

		// Very short delay for fast tests
		Duration delay = Duration.ofMillis(25); // 25 ms
		long expectedNanos = delay.toNanos();

		Routine<Long> routine = Routine.now()
				.after(delay)
				.apply(v -> System.nanoTime());

		long end = routine.join(); // blocks until the scheduled routine completes

		long actualNanos = end - start;

		assertTrue(actualNanos >= expectedNanos,
				"Routine completed too early: " + actualNanos + " ns, expected at least " + expectedNanos + " ns");
		assertTrue(actualNanos <= expectedNanos + 20_000_000, // 20 ms margin
				"Routine took too long: " + actualNanos + " ns, expected at most " + (expectedNanos + 20_000_000) + " ns");
		// log margin
		System.out.println("Margin: " + (actualNanos - expectedNanos) + " ns");
	}


}
