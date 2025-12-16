package github.devdinc.routines.external;

import org.junit.jupiter.api.Test;

import github.devdinc.routines.RoutineService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluentRoutineTest {

    @Test
    void testFluentRoutine() throws InterruptedException {
        RoutineService service = new RoutineService();
        CountDownLatch latch = new CountDownLatch(1);

        String join = service.fluent(Instant.now())
                .supply(() -> {
                    return "Hello";
                })
                .apply(input -> {
                    assertEquals("Hello", input);
                    latch.countDown();
                    return input + " World!";
                }).join();

        latch.await();
        assertEquals("Hello World!", join);
    }

    @Test
    void testMultipleApplyStages() {
        RoutineService service = new RoutineService();

        String result = service.fluent(Instant.now())
                .supply(() -> "Hello")
                .apply(s -> s + " World")
                .apply(s -> s + "!")
                .join();

        assertEquals("Hello World!", result);
    }

    @Test
    void testExecutionOrder() {
        RoutineService service = new RoutineService();
        AtomicInteger order = new AtomicInteger(0);

        Integer result = service.fluent(Instant.now())
                .supply(() -> {
                    assertEquals(0, order.getAndIncrement());
                    return 1;
                })
                .apply(i -> {
                    assertEquals(1, order.getAndIncrement());
                    return i + 1;
                })
                .apply(i -> {
                    assertEquals(2, order.getAndIncrement());
                    return i + 1;
                })
                .join();

        assertEquals(3, result);
        assertEquals(3, order.get());
    }

    @Test
    void testAfterDelay() {
        RoutineService service = new RoutineService();

        Instant start = Instant.now();
        Duration delay = Duration.ofMillis(200);

        String result = service.fluent(start)
                .supply(() -> "delayed")
                .after(delay)
                .apply(s -> s)
                .join();

        Duration elapsed = Duration.between(start, Instant.now());

        assertEquals("delayed", result);
        assertTrue(elapsed.toMillis() >= delay.toMillis());
    }

    @Test
    void testEveryExecution() throws InterruptedException {
        RoutineService service = new RoutineService();
        AtomicInteger executions = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        Integer result = service.fluent(Instant.now())
                .supply(() -> {
                    int count = executions.incrementAndGet();
                    if (count == 1) {
                        latch.countDown();
                    }
                    return count;
                })
                .every(Duration.ofMillis(50))
                .apply(i -> i)
                .join();

        latch.await();
        assertEquals(1, result);
        assertTrue(executions.get() >= 1);
    }

    @Test
    void testJoinBlocksUntilComplete() throws InterruptedException {
        RoutineService service = new RoutineService();
        CountDownLatch latch = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            service.fluent(Instant.now())
                    .supply(() -> {
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                        }
                        latch.countDown();
                        return "done";
                    })
                    .join();
        });

        t.start();

        assertFalse(latch.await(50, java.util.concurrent.TimeUnit.MILLISECONDS));
        assertTrue(latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void testJoinOnRootStageFails() {
        RoutineService service = new RoutineService();

        assertThrows(IllegalStateException.class, () -> {
            service.fluent(Instant.now()).join();
        });
    }

}