package github.devdinc.routines.external;

import github.devdinc.routines.RoutineService;
import github.devdinc.routines.Task;
import github.devdinc.routines.config.ExceptionHandlingConfiguration.ExceptionHandleRecord;
import github.devdinc.routines.config.ExceptionHandlingConfiguration.Strategy;
import github.devdinc.routines.util.Result;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void testBasicTaskExecutionAndJoin() {
        RoutineService rs = new RoutineService();

        Task<String, Integer> task = new Task<>("hello") {
            @Override
            protected Integer apply(String input) {
                return input.length();
            }
        };

        rs.schedule(task);

        Integer result = task.join();
        assertEquals(5, result);
        assertTrue(task.isComplete());
    }

    @Test
    void testTaskChaining() {
        RoutineService rs = new RoutineService();

        Task<String, Integer> task = new Task<>("abcd") {
            @Override
            protected Integer apply(String input) {
                return input.length();
            }

            @Override
            protected Task<Integer, String> next(Integer result) {
                return new Task<>(result) {
                    @Override
                    protected String apply(Integer input) {
                        return "len=" + input;
                    }
                };
            }
        };

        rs.schedule(task);

        Integer first = task.join();
        assertEquals(4, first);
        assertTrue(task.isComplete());
    }

    @Test
    void testExceptionHandling_StopStrategy() {
        RoutineService rs = new RoutineService();

        Task<Void, String> task = new Task<>(null) {
            @Override
            protected String apply(Void input) {
                throw new RuntimeException("boom");
            }

            @Override
            public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception ex) {
                return new ExceptionHandleRecord(Strategy.STOP_ALL, "failed");
            }
        };

        rs.schedule(task);

        String result = task.join();
        assertEquals("failed", result);
        assertTrue(task.isComplete());
    }

    @Test
    void testExceptionHandling_LogStrategy() {
        RoutineService rs = new RoutineService();
        AtomicInteger executions = new AtomicInteger();

        Task<Void, Void> task = new Task<>(null) {
            @Override
            protected Void apply(Void input) {
                executions.incrementAndGet();
                throw new RuntimeException("log only");
            }

            @Override
            public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception ex) {
                return new ExceptionHandleRecord(Strategy.CONTINUE, null);
            }

            @Override
            public Duration every() {
                return Duration.ofMillis(50);
            }
        };

        rs.schedule(task);

        assertThrows(IllegalStateException.class,
                () -> task.join(Duration.ofMillis(200)));

        assertTrue(executions.get() > 1);
    }

    @Test
    void testExceptionHandling_CarryStrategy() {
        RoutineService rs = new RoutineService();

        Task<Void, Result<String, Exception>> task = new Task<>(null) {
            @Override
            protected Result<String, Exception> apply(Void input) {
                return Result.err(new IllegalStateException("error"));
            }
        };

        rs.schedule(task);

        Result<String, Exception> result = task.join();
        assertTrue(result.isErr());
        assertEquals("error", result.unwrapErr().getMessage());
    }

    @Test
    void testDelayedExecution() {
        RoutineService rs = new RoutineService();

        Task<Void, Long> task = new Task<>(null) {
            @Override
            protected Long apply(Void input) {
                return System.currentTimeMillis();
            }

            @Override
            public Duration after() {
                return Duration.ofMillis(200);
            }
        };

        long start = System.currentTimeMillis();
        rs.schedule(task);

        long executedAt = task.join();
        assertTrue(executedAt - start >= 180);
    }

    @Test
    void testPeriodicExecution() throws InterruptedException {
        RoutineService rs = new RoutineService();
        AtomicInteger counter = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(3);

        Task<Void, Integer> task = new Task<>(null) {
            @Override
            protected Integer apply(Void input) {
                counter.incrementAndGet();
                latch.countDown();
                return null;
            }

            @Override
            public Duration every() {
                return Duration.ofMillis(50);
            }
        };

        rs.schedule(task);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        task.cancel();

        assertTrue(counter.get() >= 3);
    }

    @Test
    void testPeriodicExecutionStopsOnNonNull() {
        RoutineService rs = new RoutineService();
        AtomicInteger counter = new AtomicInteger();

        Task<Void, Integer> task = new Task<>(null) {
            @Override
            protected Integer apply(Void input) {
                int v = counter.incrementAndGet();
                return v >= 3 ? v : null;
            }

            @Override
            public Duration every() {
                return Duration.ofMillis(50);
            }
        };

        rs.schedule(task);

        Integer result = task.join();
        assertEquals(3, result);
        assertEquals(3, counter.get());
    }

    @Test
    void testPeriodicExecutionStopsOnException_StopStrategy() {
        RoutineService rs = new RoutineService();
        AtomicInteger counter = new AtomicInteger();

        Task<Void, Integer> task = new Task<>(null) {
            @Override
            protected Integer apply(Void input) {
                int v = counter.incrementAndGet();
                if (v == 2) {
                    throw new RuntimeException("stop");
                }
                return null;
            }

            @Override
            public Duration every() {
                return Duration.ofMillis(50);
            }

            @Override
            public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception ex) {
                return new ExceptionHandleRecord(Strategy.STOP_ALL, -1);
            }
        };

        rs.schedule(task);

        Integer result = task.join();
        assertEquals(-1, result);
        assertEquals(2, counter.get());
    }

    static class SerializableLengthTask extends Task<String, Integer> {
        SerializableLengthTask(String input) {
            super(input);
        }

        @Override
        protected Integer apply(String input) {
            return input.length();
        }
    }

    @Test
    void testTaskIsSerializable() throws Exception {
        Task<String, Integer> task = new SerializableLengthTask("abc");

        byte[] data;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(task);
            data = bos.toByteArray();
        }

        Task<?, ?> restored;
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data))) {
            restored = (Task<?, ?>) ois.readObject();
        }

        assertNotNull(restored);
    }

}
