package com.devdinc.routines;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Routine} and {@link IncompleteRoutine} classes.
 * Verifies synchronous, asynchronous, conditional, delayed, and repeating behavior.
 */
public class RoutineTest {

	// --- Utility -------------------------------------------------------------

	/**
	 * Runs a block of code with System.err silenced (for tests that trigger internal logging).
	 */
	private static void withSilentErr(Runnable action) throws Exception {
		PrintStream originalErr = System.err;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
		     PrintStream silentErr = new PrintStream(baos)) {
			System.setErr(silentErr);
			action.run();
		} finally {
			System.setErr(originalErr);
		}
	}

	// --- Basic Routine Behavior ---------------------------------------------

	@Test
	void testRunImmediateSync() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			AtomicInteger flag = new AtomicInteger();

			Routine.now()
					.run(() -> flag.set(42))
					.join();

			assertEquals(42, flag.get(), "Expected side effect to occur synchronously");
		});
	}

	@Test
	void testSupplyReturnsValue() {
		String result = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now().supply(() -> "hello").join()
		);

		assertEquals("hello", result);
	}

	/**
	 * Verifies that exceptions thrown inside an apply() are propagated as CompletionException.
	 * Logging is silenced during this test.
	 */
	@Test
	void testApplyThrowsException() throws Exception {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				withSilentErr(() -> {
					CompletableFuture<Void> testFuture = CompletableFuture.runAsync(() -> {
						Routine<Void> r = Routine.now()
								.apply(v -> { throw new IllegalStateException("boom"); });

						CompletionException thrown = assertThrows(
								CompletionException.class,
								r::join,
								"Expected an exception from join()"
						);
						assertInstanceOf(IllegalStateException.class, thrown.getCause());
						assertEquals("boom", thrown.getCause().getMessage());
					});

					assertDoesNotThrow(() -> testFuture.get(1500, TimeUnit.MILLISECONDS));
				})
		);
	}

	@Test
	void testThenChainsPreviousResult() {
		String joined = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.supply(() -> "first")
						.then()
						.apply(prev -> prev + "-second")
						.join()
		);
		assertEquals("first-second", joined);
	}

	// --- Combinator Utilities -----------------------------------------------

	@Test
	void testAllOfAndAnyOf() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			Routine<String> r1 = Routine.now().supply(() -> "A");
			Routine<String> r2 = Routine.now().supply(() -> "B");

			Routine<Void> all = Routine.allOf(r1, r2);
			assertDoesNotThrow(all::join, "allOf should complete successfully");

			Routine<Object> any = Routine.anyOf(r1, r2);
			Object result = any.join();
			assertTrue(result.equals("A") || result.equals("B"), "anyOf returned unexpected value");
		});
	}

	// --- Timing & Scheduling ------------------------------------------------

	@Test
	void testEveryRetriesUntilNonNull() {
		AtomicInteger counter = new AtomicInteger();

		String result = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
				Routine.now()
						.every(Duration.ofMillis(1))
						.supply(() -> {
							int attempt = counter.getAndIncrement();
							return (attempt < 2) ? null : "done-" + attempt;
						})
						.join()
		);

		assertNotNull(result);
		assertTrue(result.startsWith("done-"));
		assertTrue(counter.get() >= 3, "Expected at least 3 attempts");
	}

	@Test
	void testConditionalWithPrevResult() {
		String output = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.supply(() -> "magic")
						.then()
						.conditional("magic"::equals)
						.apply(prev -> prev + "-accepted")
						.join()
		);

		assertEquals("magic-accepted", output);
	}

	@Test
	void testCronCreatesExpectedInterval() {
		IncompleteRoutine<Void> cron = Routine.cron("* * * * *");
		assertTrue(cron.toString().contains("repeatInterval=PT1M"), "Expected 1-minute repeat interval");
	}

	@Test
	void testAsyncAfterAccept() {
		AtomicBoolean completed = new AtomicBoolean();

		assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.async()
						.after(Duration.ofMillis(1))
						.accept(v -> completed.set(true))
						.join()
		);

		assertTrue(completed.get());
	}

	@Test
	void testAfterDurationCompletion() {
		long start = System.nanoTime();
		Duration delay = Duration.ofMillis(25);
		long expected = delay.toNanos();

		long end = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				Routine.now()
						.after(delay)
						.apply(v -> System.nanoTime())
						.join()
		);

		long elapsed = end - start;
		assertTrue(elapsed >= expected, "Routine completed too early");
		assertTrue(elapsed <= expected + 20_000_000L, "Routine took too long");
	}

	// --- Silent Error Behavior ---------------------------------------------

	@Test
	void testSilentThrowWithoutJoin() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				withSilentErr(() -> {
					ByteArrayOutputStream err = new ByteArrayOutputStream();
					System.setErr(new PrintStream(err));

					Routine.now()
							.async()
							.apply(v -> { throw new IllegalArgumentException("silentBoom"); });

					try {
						Thread.sleep(200); // allow async task to execute
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}

					String logs = err.toString();
					assertTrue(logs.contains("silentBoom"), "Expected 'silentBoom' in logs");
					assertTrue(logs.contains("Routine error"), "Expected routine error log");
				})
		);
	}

	@Test
	void testNonSilentThrowWithoutJoin() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
				withSilentErr(() -> {
					AtomicBoolean completed = new AtomicBoolean();
					ByteArrayOutputStream err = new ByteArrayOutputStream();
					System.setErr(new PrintStream(err));

					Routine.now()
							.async()
							.apply(v -> {
								try {
									throw new IllegalArgumentException("kaboom");
								} catch (IllegalArgumentException e) {
									return true;
								}
							})
							.then()
							.accept(completed::set);
					try {
						Thread.sleep(200); // allow async task to execute
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}

					String logs = err.toString();
					assertFalse(logs.contains("kaboom"), "Unexpected 'kaboom' in logs");
					assertTrue(completed.get());
				})
		);
	}

	@Test
	void testEveryRoutineStopsOnException() {
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> withSilentErr(() -> {
			AtomicInteger counter = new AtomicInteger();

			Routine<Void> r = Routine.now()
					.every(Duration.ofMillis(50))
					.apply(v -> {
						if (counter.incrementAndGet() == 2) throw new RuntimeException("boomInLoop");
						return null;
					})
					.then()
					.run(() -> fail("Should not reach here"));

			CompletionException ex = assertThrows(CompletionException.class, r::join);
			assertInstanceOf(RuntimeException.class, ex.getCause());
			assertEquals("boomInLoop", ex.getCause().getMessage());
			assertTrue(counter.get() <= 2, "Routine should stop after throwing");
		}));
	}
}
