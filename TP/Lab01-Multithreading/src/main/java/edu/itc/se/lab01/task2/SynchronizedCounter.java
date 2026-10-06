package edu.itc.se.lab01.task2;

// 1) synchronized method – intrinsic lock on 'this' (the Class if static)
public class SynchronizedCounter implements Counter{
    private long count;
    @Override 
    public synchronized void increment() { count++; }
    @Override 
    public synchronized long get(){ return count;} // readers must lock too!
}
