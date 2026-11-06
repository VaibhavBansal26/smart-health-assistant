package com.healthcare.app.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter per user. Every chat message costs money and an
 * unbounded loop (bug or abuse) would drain the API budget.
 * In-memory: fine for one server; use Redis or a gateway when scaling out.
 */
@Component
public class ChatRateLimiter {

    private final int maxPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ChatRateLimiter(@Value("${app.chat.rate-limit-per-minute}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    public boolean tryAcquire(String user) {
        long now = System.currentTimeMillis();
        long windowStart = now - Duration.ofMinutes(1).toMillis();
        Deque<Long> q = hits.computeIfAbsent(user, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < windowStart) {
                q.pollFirst();
            }
            if (q.size() >= maxPerMinute) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
