package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.LongAdder;

public class LongAdderCounter implements Counter{
    private final LongAdder count = new LongAdder();
    @Override 
    public void increment() {
        count.increment();
    }
    @Override 
    public long get() {
        return count.sum();
    }

}
