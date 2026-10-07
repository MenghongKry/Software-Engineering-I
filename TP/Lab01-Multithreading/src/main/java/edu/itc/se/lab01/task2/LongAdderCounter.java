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
/*Thread-safe, and the fastest here when many threads write at once.

Why LongAdder beats AtomicLong under contention:
 - AtomicLong has ONE memory location. Every thread fights over the same
cache line: each successful update forces that line to move between CPU
cores, and failed compare-and-swaps have to retry. More threads means more
fighting, so throughput drops as contention rises.
- LongAdder spreads the count across several "cells" (a base value plus an
array of padded counters). When threads collide, LongAdder gives them
different cells, so each core mostly updates its own cache line without
retrying. The cells are padded so they don't share a cache line
(avoiding "false sharing").
- sum() adds up base + all cells. That makes reading slower, and the result
is only exact when no thread is writing at that moment (fine here, because
Bench reads after join()).
Trade-off: use LongAdder for counters that are written a lot and read rarely
(stats, metrics, hit counts). Use AtomicLong when you need an exact value on
every read or operations like compareAndSet.
*/