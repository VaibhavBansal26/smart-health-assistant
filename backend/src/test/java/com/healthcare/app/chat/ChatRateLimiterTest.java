package com.healthcare.app.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chat rate limiter")
class ChatRateLimiterTest {

    @Test
    void allowsUpToTheLimit_thenBlocks() {
        var limiter = new ChatRateLimiter(3);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void usersAreCountedSeparately() {
        var limiter = new ChatRateLimiter(1);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void isSafeUnderConcurrentUse() throws Exception {
        var limiter = new ChatRateLimiter(50);
        var allowed = new java.util.concurrent.atomic.AtomicInteger();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < 200; i++) {
            tasks.add(pool.submit(() -> {
                if (limiter.tryAcquire("same-user")) allowed.incrementAndGet();
            }));
        }
        for (var t : tasks) t.get();
        pool.shutdown();
        assertEquals(50, allowed.get(), "exactly the limit must get through, no more, no less");
    }
}
