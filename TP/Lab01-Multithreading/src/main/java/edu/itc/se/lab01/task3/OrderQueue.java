package edu.itc.se.lab01.task3;

public interface OrderQueue<T> {

    // waits while full
    void put(T item) throws InterruptedException;   
    // waits while empty
    T take() throws InterruptedException;           
}
