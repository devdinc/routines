package github.devdinc.routines.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration.ExceptionHandleRecord;
import github.devdinc.routines.config.ExceptionHandlingConfiguration.Strategy;
import github.devdinc.routines.config.impl.ehc.Carry;
import github.devdinc.routines.config.impl.ehc.LogAndContinue;
import github.devdinc.routines.util.Result;

public class TaskTest {

    // A basic Task implementation for testing purposes
    static class TestTask extends Task<String, String> {
        private final boolean shouldThrow;
        private final Runnable onApply;

        public void register() {
            throw new UnsupportedOperationException("Register not supported in this test");
        }

        protected TestTask(String input) {
            this(input, false, null);
        }

        protected TestTask(String input, boolean shouldThrow) {
            this(input, shouldThrow, null);
        }

        protected TestTask(String input, Runnable onApply) {
            this(input, false, onApply);
        }

        protected TestTask(String input, boolean shouldThrow, Runnable onApply) {
            super(input, false);
            this.shouldThrow = shouldThrow;
            this.onApply = onApply;
        }

        @Override
        protected String apply(String input) {
            if (onApply != null)
                onApply.run();
            if (shouldThrow) {
                throw new RuntimeException("Test Exception");
            }
            if (input == null)
                return null;
            return "Processed: " + input;
        }
    }

    @Test
    void testBasicTaskExecutionAndJoin() {
        TestTask task = new TestTask("input");
        task.register();
        String result = task.join();
        assertEquals("Processed: input", result);
    }

    @Test
    void testTaskChaining() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        TestTask firstTask = new TestTask("first") {
            @Override
            public Task<String, ?> next(String output) {
                return new Task<String, Void>(output, false) {
                    @Override
                    protected Void apply(String input) {
                        assertEquals("Processed: first", input);
                        latch.countDown();
                        return null;
                    }
                };
            }
        };
        firstTask.register();
        // join() on the first task waits for it to complete. The next task is scheduled
        // after that.
        firstTask.join();
        // We need to wait for the second task to execute.
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Second task in chain did not execute.");
    }

    @Test
    void testExceptionHandling_StopStrategy() {
        TestTask task = new TestTask("input", true) {
            @Override
            protected ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception exception) {
                return new ExceptionHandleRecord(Strategy.STOP_ALL, null);
            }

            @Override
            public Duration after() {
                return Duration.ofMillis(50);
            }
        };
        task.register();
        String result = task.join();
        assertNull(result);
    }

    @Test
    void testExceptionHandling_LogStrategy() {
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            TestTask task = new TestTask("input", true) {
                @Override
                protected ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception exception) {
                    return new LogAndContinue()._onUncaughtException(task, exception);
                }
            };

            task.register();
            String result = task.join();

            assertNull(result);

            String logged = errContent.toString();
            assertTrue(logged.contains("Test Exception"),
                    "Expected exception to be logged to System.err");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testExceptionHandling_CarryStrategy() {
        Task<String, Result<String, Throwable>> task = new Task<>("input", true) {
            @Override
            protected Result<String, Throwable> apply(String input) {
                throw new RuntimeException("Carry Exception");
            }

            @Override
            protected ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception exception) {
                return new Carry()._onUncaughtException(task, exception);
            }
        };
        Result<String, Throwable> result = task.join();
        assertTrue(result.isErr());
        assertEquals("Carry Exception", result.unwrapErr().getMessage());
    }

    @Test
    void testDelayedExecution() {
        long startTime = System.nanoTime();
        Duration delay = Duration.ofMillis(100);
        TestTask task = new TestTask("input") {
            @Override
            public Duration after() {
                return delay;
            }
        };
        task.register();
        task.join();
        long elapsed = System.nanoTime() - startTime;
        assertTrue(elapsed >= delay.toNanos());
    }

    @Test
    void testPeriodicExecution() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        TestTask task = new TestTask(null, counter::incrementAndGet) {
            @Override
            public Duration every() {
                return Duration.ofMillis(10);
            }
        };

        task.register();
        Thread.sleep(55); // Allow for a few executions
        task.cancel(); // Manually cancel to stop the test.
        int count = counter.get();
        assertTrue(count > 0,
                "Counter should be > 0, but was " + count);
    }

    @Test
    void testPeriodicExecutionStopsOnNonNull() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        TestTask task = new TestTask(null) {
            @Override
            public Duration every() {
                return Duration.ofMillis(10);
            }

            @Override
            protected String apply(String input) {
                int count = counter.incrementAndGet();
                if (count >= 3) {
                    return "done";
                }
                return null;
            }
        };

        task.register();
        String result = task.join(); // This will wait until the task is done.

        assertEquals("done", result);
        assertEquals(3, counter.get(), "Task should execute exactly 3 times.");

        // Wait a bit more to ensure it's not running anymore
        Thread.sleep(50);
        assertEquals(3, counter.get(), "Task should have stopped after returning a non-null value.");
    }

    @Test
    void testPeriodicExecutionStopsOnException_StopStrategy() throws InterruptedException, RuntimeException {
        AtomicInteger counter = new AtomicInteger(0);
        TestTask task = new TestTask("input") {
            @Override
            public Duration every() {
                return Duration.ofMillis(10);
            }

            @Override
            protected ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception exception) {
                return new ExceptionHandleRecord(Strategy.STOP_ALL, null);
            }

            @Override
            protected String apply(String input) {
                int count = counter.incrementAndGet();
                if (count == 2) {
                    throw new RuntimeException("Failure during periodic execution");
                }
                return null;
            }
        };

        task.register();
        String result = task.join();

        assertNull(result);
        assertEquals(2, counter.get());

        Thread.sleep(50);
        assertEquals(2, counter.get(), "Task should have stopped after the exception.");
    }

    @Test
    void testTaskIsSerializable() throws Exception {
        TestTask task = new TestTask("input");

        byte[] bytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(task);
            bytes = bos.toByteArray();
        }

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            @SuppressWarnings("unchecked")
            Task<String, String> restored = (Task<String, String>) in.readObject();
            assertNotNull(restored);
        }
    }
}
