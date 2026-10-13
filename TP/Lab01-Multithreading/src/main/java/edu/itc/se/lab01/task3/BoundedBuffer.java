package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;

public class BoundedBuffer<T> implements OrderQueue<T> {
    private final ArrayDeque<T> items = new ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) {
            wait();// releases the lock while sleeping
        }
        items.addLast(item);
        assert items.size() <= capacity : "capacity exceeded: size=" + items.size();
        notifyAll();// wake consumers waiting for an item
    }

    @Override
    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        // throws NoSuchElementException if empty
        T item = items.removeFirst();
        // wake producers waiting for space
        notifyAll();
        return item;
    }
}
