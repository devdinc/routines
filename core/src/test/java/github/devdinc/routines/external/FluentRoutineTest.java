package github.devdinc.routines.external;

import org.junit.jupiter.api.Test;

import github.devdinc.routines.RoutineService;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluentRoutineTest {

    @Test
    void testFluentRoutine() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        RoutineService service = new RoutineService();
        CountDownLatch latch = new CountDownLatch(1);

        service.fluent(Instant.now())
                .supply(() -> {
                    counter.incrementAndGet();
                    return "Hello";
                })
                .apply(input -> {
                    assertEquals("Hello", input);
                    counter.incrementAndGet();
                    return "World";
                })
                .apply(input -> {
                    assertEquals("World", input);
                    counter.incrementAndGet();
                    return "!";
                })
                .accept(i -> assertEquals("!", i))
                .run(() -> assertEquals(3, counter.get()))
                .run(() -> latch.countDown());

        latch.await();
    }

    @Test
    void testFluentRoutineJoin() {
        RoutineService service = new RoutineService();

        String result = service.fluent(Instant.now())
                .supply(() -> "Hello")
                .join();

        assertEquals(result, "Hello");
    }
}