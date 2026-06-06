package com.cody.core.interaction;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Queue for injecting user input into running agent sessions.
 *
 * Allows external callers (e.g. WebSocket) to push user messages
 * into an active agent run, where the agent loop polls for new input.
 */
public class UserInputQueue {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    /** Push a user message into the queue. */
    public void submit(String message) {
        queue.add(message);
    }

    /** Poll for a new message, returning null if none available. */
    public String poll() {
        return queue.poll();
    }

    /** Wait up to the given timeout for a new message. */
    public String poll(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Check if there are messages waiting. */
    public boolean hasPending() {
        return !queue.isEmpty();
    }

    /** Clear all pending messages. */
    public void clear() {
        queue.clear();
    }

    /** Number of pending messages. */
    public int size() {
        return queue.size();
    }
}
