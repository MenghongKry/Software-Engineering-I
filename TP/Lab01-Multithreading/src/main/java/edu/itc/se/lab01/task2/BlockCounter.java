package edu.itc.se.lab01.task2;

// 2) synchronized block – smaller critical section, private lock object    
public class BlockCounter implements Counter {
    // blockObjet is equal to lock
    private final Object blockObject = new Object(); // nobody else can lock it
    private long count;
    @Override 
    public void increment(){
        synchronized (blockObject) { count++; }
    }
    @Override 
    public long get(){
        synchronized (blockObject) { return count; }
    }
}
