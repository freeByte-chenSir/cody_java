package com.jody.core.security;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding window rate limiter for tool calls.
 *
 */
public class RateLimiter {

    private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();
    private final int maxRequests;
    private final long windowMs;

    public RateLimiter(int maxRequests, double windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMs = (long) (windowSeconds * 1000);
    }

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxRequests) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    public synchronized int remaining() {
        long now = System.currentTimeMillis();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
            timestamps.pollFirst();
        }
        return Math.max(0, maxRequests - timestamps.size());
    }
}
