package github.devdinc.routines.external;

import org.junit.jupiter.api.Test;

import github.devdinc.routines.CronTask;
import github.devdinc.routines.cron.Cron;
import github.devdinc.routines.cron.UnixCronParser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CronTaskTest {

    @Test
    public void testCronTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);
        Cron cron = UnixCronParser.parse("* * * * * *");

        long startTime = System.currentTimeMillis();

        new CronTask(cron) {
            @Override
            protected void run() {
                int a = counter.incrementAndGet();
                if (a == 3)
                    latch.countDown();
            }
        };

        latch.await();
        long endTime = System.currentTimeMillis();
        assertTrue((endTime - startTime) < 2500 && (endTime - startTime) > 2000,
                "Elapsed time: " + (endTime - startTime) + "ms");

        assertEquals(3, counter.get());
    }

    @Test
    public void testNoOverlap() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        Cron cron = UnixCronParser.parse("* * * * * *");

        new CronTask(cron) {
            @Override
            protected void run() {
                try {
                    counter.incrementAndGet();
                    if (counter.get() == 1) {
                        // simulate long running job (2.5 seconds)
                        Thread.sleep(2500);
                        latch.countDown();
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        latch.await();

        // There should be exactly 1 run because task exceeded interval length
        assertEquals(1, counter.get());
    }

    @Test
    public void testMultipleTasks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Cron cron = UnixCronParser.parse("* * * * * *");

        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();

        new CronTask(cron) {
            @Override
            protected void run() {
                if (c1.incrementAndGet() == 2)
                    latch.countDown();
            }
        };

        new CronTask(cron) {
            @Override
            protected void run() {
                if (c2.incrementAndGet() == 2)
                    latch.countDown();
            }
        };

        latch.await();

        assertEquals(2, c1.get(), "Task 1 should run twice");
        assertEquals(2, c2.get(), "Task 2 should run twice");
    }

}
