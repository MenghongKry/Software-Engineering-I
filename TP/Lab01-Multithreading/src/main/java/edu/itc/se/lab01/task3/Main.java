package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    static final int PRODUCERS = 2;
    static final int ORDERS_PER_PRODUCER = 50;
    static final int CONSUMERS = 3;
    static final int CAPACITY = 5;

    public static void main(String[] args) throws InterruptedException {
        run("BoundedBuffer", new BoundedBuffer<>(CAPACITY));
    }
    static void run(String title, OrderQueue<Order> queue) throws InterruptedException {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger producersLeft = new AtomicInteger(PRODUCERS);
        List<Thread> threads = new ArrayList<>();

        for (int p = 0; p < PRODUCERS; p++) {
            int firstId = p * ORDERS_PER_PRODUCER + 1;
            threads.add(new Thread(new Producer(queue, ORDERS_PER_PRODUCER, firstId,
                    CONSUMERS, producersLeft), "producer-" + p));
        }
        for (int c = 0; c < CONSUMERS; c++) {
            threads.add(new Thread(new Consumer(queue, processed), "consumer-" + c));
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int total = processed.get();
        int expected = PRODUCERS * ORDERS_PER_PRODUCER;
        if (total != expected) {
            throw new AssertionError("expected " + expected + " but processed " + total);
        }
        System.out.println("[" + title + "] processed " + total + " orders");
    }
}
