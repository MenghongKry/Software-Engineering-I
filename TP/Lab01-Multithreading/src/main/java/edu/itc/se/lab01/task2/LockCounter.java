package edu.itc.se.lab01.task2;
import java.util.concurrent.locks.ReentrantLock;

// 3) java.util.concurrent.locks – tryLock, timeouts, fairness, Conditions
public class LockCounter implements Counter{

    private final ReentrantLock reentrantLock = new ReentrantLock();
    private long count;

    @Override 
    public void increment(){
        reentrantLock.lock();
        try{
            count++;
        }finally{ // ALWAYS unlock in finally
            reentrantLock.unlock();
        }
    }
    @Override 
    public long get(){
        return count;
    }
}
