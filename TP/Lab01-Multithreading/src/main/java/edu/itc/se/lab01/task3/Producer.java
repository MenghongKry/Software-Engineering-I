package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements Runnable {
    private final OrderQueue<Order> queue;
    private final int count;
    private final int firstId;
    private final int consumers;
    private final AtomicInteger producersLeft;

    public Producer(OrderQueue<Order> queue, int count, int firstId,
                    int consumers, AtomicInteger producersLeft) {
        this.queue = queue;
        this.count = count;
        this.firstId = firstId;
        this.consumers = consumers;
        this.producersLeft = producersLeft;
    }

    @Override
    public void run() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            for (int i = firstId; i < firstId + count; i++) {
                queue.put(new Order(i, "item-" + i, random.nextInt(1, 6)));
                Thread.sleep(random.nextInt(0, 21));
            }
            if (producersLeft.decrementAndGet() == 0) {
                for (int c = 0; c < consumers; c++) {
                    queue.put(Order.POISON_PILL);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
