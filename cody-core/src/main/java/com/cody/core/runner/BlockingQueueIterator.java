package com.cody.core.runner;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;

/**
 * Iterator adapter that reads from a BlockingQueue.
 * Used to expose the streaming agent loop as a simple Iterator<StreamEvent>.
 * Returns false from hasNext() when the POISON_PILL is received.
 */
public class BlockingQueueIterator<T> implements Iterator<T> {

    private final BlockingQueue<T> queue;
    private T nextItem;
    private boolean done;

    public BlockingQueueIterator(BlockingQueue<T> queue) {
        this.queue = queue;
    }

    @Override
    public boolean hasNext() {
        if (done) return false;
        if (nextItem != null) return true;
        try {
            nextItem = queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            done = true;
            return false;
        }
        if (nextItem == StreamEvent.POISON_PILL || (nextItem instanceof StreamEvent && ((StreamEvent) nextItem).getType().equals("__END__"))) {
            done = true;
            nextItem = null;
            return false;
        }
        return true;
    }

    @Override
    public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        T item = nextItem;
        nextItem = null;
        return item;
    }
}
