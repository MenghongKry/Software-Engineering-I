package edu.itc.se.lab01.task2;

public class UnSafeCounter implements Counter{
    private long count;
    @Override 
    public void increment(){
        count++;
    }
    @Override 
    public long get(){
        return count; 
    }
}
