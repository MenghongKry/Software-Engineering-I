package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final OrderQueue<Order> queue;
    private final AtomicInteger processed;

    public Consumer(OrderQueue<Order> queue, AtomicInteger processed) {
        this.queue = queue;
        this.processed = processed;
    }

    @Override
    public void run() {
        String me = Thread.currentThread().getName();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            while (true) {
                Order order = queue.take();
                if (order.isPoisonPill()) {
                    break;
                }
                Thread.sleep(random.nextInt(5, 31));
                processed.incrementAndGet();
                System.out.println(me + " processed " + order);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
