package edu.itc.se.lab01.task3;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Order> q = new ArrayBlockingQueue<>(Main.CAPACITY);

        OrderQueue<Order> queue = new OrderQueue<>() {
            @Override 
            public void put(Order o) throws InterruptedException { q.put(o); }
            @Override 
            public Order take() throws InterruptedException{ return q.take(); }
        };

        Main.run("ArrayBlockingQueue", queue);
    }
}
