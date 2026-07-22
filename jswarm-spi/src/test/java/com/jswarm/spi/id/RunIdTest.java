package com.jswarm.spi.id;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RunIdTest {

    @Test
    void shouldGenerateUniqueIdsConcurrently() throws Exception {
        int count = 10_000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        var pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < count; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ids.add(RunId.generate().value());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(count, ids.size());
    }

    @Test
    void shouldRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> new RunId("  "));
    }
}
